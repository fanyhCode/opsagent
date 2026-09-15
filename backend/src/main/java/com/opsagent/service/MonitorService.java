package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opsagent.common.BizException;
import com.opsagent.dto.SystemMetrics;
import com.opsagent.entity.ServerMetric;
import com.opsagent.entity.ServerInfo;
import com.opsagent.mapper.ServerMetricMapper;
import com.opsagent.ssh.SshCommandResult;
import com.opsagent.ssh.SshExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控采集服务：通过 SSH 到目标 Linux 服务器采集基础指标。
 *
 * 三个关键设计：
 *
 * 1. **一次 SSH 连接采集全部指标**
 *    每次 SSH 建连都要握手、认证，开销不小。所以这里把多条命令拼成一段脚本一次发送，
 *    用 "@@标记@@" 分段，再在本地解析，避免为了 5 个指标建 5 次连接。
 *
 * 2. **强制英文输出**
 *    脚本开头 export LC_ALL=C。因为服务器的 locale 可能是中文，
 *    那样 free/top 的表头会变成中文，按关键字解析就会失败——这是很隐蔽的坑。
 *
 * 3. **命令写死在代码里**
 *    现阶段采集命令是固定的、只读的（cat/free/df/vmstat/ps），
 *    将来让 Agent 动态生成命令时，必须先经过命令白名单和风险分级（M5）。
 */
@Service
public class MonitorService {

    private final SshExecutor sshExecutor;
    private final ServerInfoService serverInfoService;
    private final ServerMetricMapper serverMetricMapper;

    /** 采集脚本：分段输出，便于解析 */
    private static final String COLLECT_SCRIPT = String.join("\n",
            // 强制英文输出，避免中文 locale 导致表头变化
            "export LC_ALL=C",
            "echo '@@HOST@@'",
            "hostname",
            "echo '@@LOAD@@'",
            "cat /proc/loadavg",
            "echo '@@UPTIME@@'",
            "uptime -p",
            "echo '@@MEM@@'",
            "free -m | sed -n '2p'",
            "echo '@@DISK@@'",
            "df -hP / | tail -1",
            "echo '@@CPU@@'",
            // vmstat 第一次采样是自开机以来的平均值，第二次才是瞬时值，所以要取第二行
            "vmstat 1 2 | tail -1",
            "echo '@@PROC@@'",
            // 过滤掉采集脚本自己的进程（ps/vmstat/bash 等），否则它们会混进"Top 进程"里，
            // 而且短命进程的 CPU 百分比会算出 500% 这种离谱数字
            "ps -eo pid,comm,pcpu,pmem --sort=-pcpu --no-headers | grep -vwE 'ps|vmstat|bash|sh|sshd|grep|head' | head -5",
            "echo '@@TIME@@'",
            "date '+%Y-%m-%d %H:%M:%S'"
    );

    public MonitorService(SshExecutor sshExecutor,
                          ServerInfoService serverInfoService,
                          ServerMetricMapper serverMetricMapper) {
        this.sshExecutor = sshExecutor;
        this.serverInfoService = serverInfoService;
        this.serverMetricMapper = serverMetricMapper;
    }

    /**
     * 采集指定服务器的基础指标。
     */
    public SystemMetrics collect(Long serverId) {
        ServerInfo server = serverInfoService.getById(serverId);
        if (server == null) {
            throw new BizException("服务器不存在：id=" + serverId);
        }
        return collect(server);
    }

    /**
     * 用已有的服务器对象采集指标。
     * 定时任务批量采集时用这个方法，可以省掉每台服务器重复查一次库。
     */
    public SystemMetrics collect(ServerInfo server) {
        if (isBlank(server.getUsername()) || isBlank(server.getPassword())) {
            throw new BizException("该服务器还没有配置 SSH 账号密码，请先在数据库 server 表中补全");
        }
        SshCommandResult result = sshExecutor.execute(server, COLLECT_SCRIPT, 20);
        if (result.exitStatus() != 0) {
            throw new BizException("采集命令执行失败（退出码 " + result.exitStatus() + "）：" + result.stderr());
        }
        return parse(result.stdout());
    }

    /**
     * 采集并入库，供定时任务调用。
     *
     * 注意这里只存了 4 个关键指标（CPU/内存/磁盘/负载），
     * 进程列表属于"详情"数据，体积大且实时变化快，不入库，只在实时接口里返回。
     */
    public void collectAndSave(ServerInfo server) {
        SystemMetrics metrics = collect(server);

        ServerMetric row = new ServerMetric();
        row.setServerId(server.getId());
        row.setCpuUsage(metrics.cpuUsagePercent());
        row.setMemoryUsage(metrics.memory().usagePercent());
        row.setDiskUsage(parsePercent(metrics.disk().usePercent()));
        row.setLoadAverage(metrics.load1());

        // 显式写入采集时间，不用数据库的 CURRENT_TIMESTAMP 默认值。
        // 原因：Docker 容器里的 MySQL 默认时区是 UTC，如果交给数据库生成时间，
        // 存进去的会比我们本地时间少 8 小时，趋势图的横坐标就对不上了。
        // 由应用统一提供时间戳，可以保证"入库时间"与"采集时刻"一致。
        row.setCreatedAt(LocalDateTime.now());

        serverMetricMapper.insert(row);
    }

    /**
     * 查询历史采集数据（按时间倒序取最近 limit 条，前端画图时再反转成时间正序）。
     */
    public List<ServerMetric> history(Long serverId, int limit) {
        // limit 来自前端参数，必须限幅：既防止有人传个 100 万把数据库拖死，
        // 也避免把用户输入直接拼进 SQL（这里最终用的是 int，不存在注入风险）
        int safeLimit = Math.min(Math.max(limit, 1), 500);

        return serverMetricMapper.selectList(new LambdaQueryWrapper<ServerMetric>()
                .eq(ServerMetric::getServerId, serverId)
                .orderByDesc(ServerMetric::getCreatedAt)
                .last("LIMIT " + safeLimit));
    }

    /** 把脚本输出按 @@标记@@ 切成若干段 */
    private Map<String, List<String>> splitSections(String stdout) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String rawLine : stdout.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("@@") && line.endsWith("@@")) {
                current = line;
                sections.putIfAbsent(current, new ArrayList<>());
            } else if (current != null && !line.isEmpty()) {
                sections.get(current).add(line);
            }
        }
        return sections;
    }

    private SystemMetrics parse(String stdout) {
        Map<String, List<String>> sections = splitSections(stdout);

        String hostname = firstLine(sections, "@@HOST@@");
        String uptime = firstLine(sections, "@@UPTIME@@");
        String collectedAt = firstLine(sections, "@@TIME@@");

        // ---- 负载：0.15 0.10 0.08 1/234 5678 ----
        double load1 = 0, load5 = 0, load15 = 0;
        String loadLine = firstLine(sections, "@@LOAD@@");
        if (loadLine != null) {
            String[] parts = loadLine.split("\\s+");
            if (parts.length >= 3) {
                load1 = parseDouble(parts[0]);
                load5 = parseDouble(parts[1]);
                load15 = parseDouble(parts[2]);
            }
        }

        // ---- 内存：Mem: 总 已用 空闲 共享 缓存 可用 ----
        SystemMetrics.Memory memory = new SystemMetrics.Memory(0, 0, 0, 0);
        String memLine = firstLine(sections, "@@MEM@@");
        if (memLine != null) {
            String[] p = memLine.split("\\s+");
            if (p.length >= 7) {
                long total = (long) parseDouble(p[1]);
                long used = (long) parseDouble(p[2]);
                long available = (long) parseDouble(p[6]);
                double percent = total > 0 ? round1(used * 100.0 / total) : 0;
                memory = new SystemMetrics.Memory(total, used, available, percent);
            }
        }

        // ---- 磁盘：文件系统 容量 已用 可用 使用率 挂载点 ----
        SystemMetrics.Disk disk = new SystemMetrics.Disk("", "", "", "", "", "");
        String diskLine = firstLine(sections, "@@DISK@@");
        if (diskLine != null) {
            String[] p = diskLine.split("\\s+");
            if (p.length >= 6) {
                disk = new SystemMetrics.Disk(p[0], p[1], p[2], p[3], p[4], p[5]);
            }
        }

        // ---- CPU：vmstat 第二行，第 15 个字段是空闲率 ----
        double cpuUsage = 0;
        String vmLine = firstLine(sections, "@@CPU@@");
        if (vmLine != null) {
            String[] p = vmLine.split("\\s+");
            if (p.length >= 15) {
                double idle = parseDouble(p[14]);
                cpuUsage = round1(Math.max(0, 100 - idle));
            }
        }

        // ---- 进程：pid 名称 CPU% 内存% ----
        List<SystemMetrics.Process> processes = new ArrayList<>();
        List<String> procLines = sections.getOrDefault("@@PROC@@", List.of());
        for (String line : procLines) {
            String[] p = line.split("\\s+");
            if (p.length >= 4) {
                processes.add(new SystemMetrics.Process(
                        p[0], p[1], parseDouble(p[2]), parseDouble(p[3])));
            }
        }

        return new SystemMetrics(hostname, cpuUsage, load1, load5, load15,
                uptime, memory, disk, processes, collectedAt);
    }

    private String firstLine(Map<String, List<String>> sections, String key) {
        List<String> lines = sections.get(key);
        return (lines == null || lines.isEmpty()) ? null : lines.get(0);
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** 把 "29%" 解析成 29.0；解析不出来就返回 0，保证采集流程不因为格式问题中断 */
    private double parsePercent(String text) {
        if (text == null) {
            return 0;
        }
        return parseDouble(text.replace("%", "").trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
