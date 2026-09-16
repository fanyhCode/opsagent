package com.opsagent.ai;

import com.opsagent.dto.SystemMetrics;
import com.opsagent.entity.ServerInfo;
import com.opsagent.service.MonitorService;
import com.opsagent.service.ServerInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * 提供给 AI Agent 使用的工具集（全部只读）。
 *
 * 这是整个项目最关键的一层：把"平台已有的能力"包装成模型可以自己决定调用的工具。
 *
 * 三个设计原则：
 * 1. **只读**：当前只暴露查询类工具。执行写操作（重启服务、删除文件）必须等 M5 的
 *    命令安全执行引擎（白名单 + 风险分级 + 人工确认）做好之后再开放，
 *    否则等于绕过了整个安全设计；
 * 2. **工具是收敛的接口，不是裸命令通道**：每个工具对应一个明确的业务动作，
 *    模型无法用它执行任意 shell；
 * 3. **出错返回文字而不是抛异常**：工具执行失败时把原因作为结果返回，
 *    这样模型可以据此解释"为什么查不到"，而不是让整个对话直接 500。
 */
@Component
public class SystemMonitorTools {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorTools.class);

    private final ServerInfoService serverInfoService;
    private final MonitorService monitorService;

    public SystemMonitorTools(ServerInfoService serverInfoService, MonitorService monitorService) {
        this.serverInfoService = serverInfoService;
        this.monitorService = monitorService;
    }

    /**
     * 工具 1：查询纳管的服务器列表。
     * description 非常重要——模型就是靠它来决定"什么时候该调用这个工具"。
     */
    @Tool(description = "查询平台纳管的所有服务器列表，返回每台服务器的 id、名称、主机地址和状态。"
            + "当用户没有明确说查哪台服务器，或需要先知道有哪些服务器时，先调用这个工具。")
    public String listServers() {
        long start = System.currentTimeMillis();
        try {
            List<ServerInfo> servers = serverInfoService.listAll();
            if (servers.isEmpty()) {
                ToolCallRecorder.record("list_servers", "无参数", "没有纳管任何服务器",
                        System.currentTimeMillis() - start, true);
                return "当前平台还没有纳管任何服务器。";
            }

            StringJoiner joiner = new StringJoiner("\n");
            for (ServerInfo server : servers) {
                joiner.add("id=%d, 名称=%s, 地址=%s:%d, 状态=%s, 系统=%s"
                        .formatted(server.getId(), server.getName(), server.getHost(),
                                server.getPort(), server.getStatus(), server.getOs()));
            }
            String result = joiner.toString();
            ToolCallRecorder.record("list_servers", "无参数", "共 " + servers.size() + " 台服务器",
                    System.currentTimeMillis() - start, true);
            return result;

        } catch (Exception e) {
            log.warn("工具 list_servers 执行失败：{}", e.getMessage());
            ToolCallRecorder.record("list_servers", "无参数", "执行失败：" + e.getMessage(),
                    System.currentTimeMillis() - start, false);
            return "查询服务器列表失败：" + e.getMessage();
        }
    }

    /**
     * 工具 2：查询某台服务器的实时指标。
     * 内部走 SSH 到目标服务器执行只读命令（top/vmstat/free/df），全部是采集类操作。
     */
    @Tool(description = "查询指定服务器的实时运行指标：CPU 使用率、内存使用率、磁盘使用率、"
            + "系统负载（1/5/15 分钟）和运行时长。当用户询问服务器是否正常、是否有性能问题时调用。")
    public String getServerOverview(
            @ToolParam(description = "服务器 id，可以通过 listServers 工具获取") Long serverId) {
        long start = System.currentTimeMillis();
        try {
            SystemMetrics m = monitorService.collect(serverId);
            String result = """
                    主机名：%s
                    CPU 使用率：%s%%
                    内存使用率：%s%%（已用 %d MB / 共 %d MB）
                    磁盘使用率：%s%%（已用 %s / 共 %s，挂载点 %s）
                    系统负载：1 分钟 %s，5 分钟 %s，15 分钟 %s
                    运行时长：%s
                    采集时间：%s"""
                    .formatted(m.hostname(), m.cpuUsagePercent(),
                            m.memory().usagePercent(), m.memory().usedMb(), m.memory().totalMb(),
                            parsePercent(m.disk().usePercent()), m.disk().used(), m.disk().size(), m.disk().mountedOn(),
                            m.load1(), m.load5(), m.load15(),
                            m.uptime(), m.collectedAt());

            ToolCallRecorder.record("get_server_overview", "serverId=" + serverId,
                    "CPU " + m.cpuUsagePercent() + "%，内存 " + m.memory().usagePercent()
                            + "%，磁盘 " + m.disk().usePercent(),
                    System.currentTimeMillis() - start, true);
            return result;

        } catch (Exception e) {
            log.warn("工具 get_server_overview 执行失败：{}", e.getMessage());
            ToolCallRecorder.record("get_server_overview", "serverId=" + serverId,
                    "执行失败：" + e.getMessage(), System.currentTimeMillis() - start, false);
            return "采集服务器 " + serverId + " 的指标失败：" + e.getMessage();
        }
    }

    /**
     * 工具 3：查询 CPU 占用最高的进程。
     */
    @Tool(description = "查询指定服务器上 CPU 占用最高的前 5 个进程（进程名、PID、CPU 百分比、内存百分比）。"
            + "当需要定位'是谁在占用 CPU'时调用，通常配合 getServerOverview 一起使用。")
    public String getTopProcesses(
            @ToolParam(description = "服务器 id，可以通过 listServers 工具获取") Long serverId) {
        long start = System.currentTimeMillis();
        try {
            SystemMetrics m = monitorService.collect(serverId);
            if (m.topProcesses() == null || m.topProcesses().isEmpty()) {
                ToolCallRecorder.record("get_top_processes", "serverId=" + serverId,
                        "没有取到进程信息", System.currentTimeMillis() - start, true);
                return "没有采集到进程信息。";
            }

            StringJoiner joiner = new StringJoiner("\n");
            for (SystemMetrics.Process p : m.topProcesses()) {
                joiner.add("PID=%s, 进程=%s, CPU=%s%%, 内存=%s%%"
                        .formatted(p.pid(), p.command(), p.cpuPercent(), p.memPercent()));
            }
            String result = joiner.toString();
            ToolCallRecorder.record("get_top_processes", "serverId=" + serverId,
                    "取到 " + m.topProcesses().size() + " 个进程", System.currentTimeMillis() - start, true);
            return result;

        } catch (Exception e) {
            log.warn("工具 get_top_processes 执行失败：{}", e.getMessage());
            ToolCallRecorder.record("get_top_processes", "serverId=" + serverId,
                    "执行失败：" + e.getMessage(), System.currentTimeMillis() - start, false);
            return "采集进程列表失败：" + e.getMessage();
        }
    }

    /** 把 "29%" 这类字符串转成数字，方便拼进返回文本 */
    private String parsePercent(String text) {
        if (text == null) {
            return "0";
        }
        return text.replace("%", "").trim();
    }
}
