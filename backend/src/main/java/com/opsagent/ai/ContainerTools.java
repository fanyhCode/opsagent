package com.opsagent.ai;

import com.opsagent.common.BizException;
import com.opsagent.entity.ServerInfo;
import com.opsagent.service.ServerInfoService;
import com.opsagent.ssh.SshCommandResult;
import com.opsagent.ssh.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 容器与日志工具（只读）。
 *
 * 这是"多源数据关联诊断"的关键一环：只看 CPU 高没有意义，
 * 要看是哪个容器在吃 CPU，再去翻它的日志，才能得出根因。
 *
 * ⚠️ 安全要点（这一段是这个类最重要的部分）：
 * 这些工具的参数是**模型生成的**。如果直接把模型给的字符串拼进 shell 命令，
 * 模型（或诱导模型的用户）就能构造出 `mycontainer; rm -rf /` 这种命令注入攻击。
 * 所以每个参数进命令之前都必须**校验格式**：
 * - 容器名只允许字母数字与 _ . -，且长度受限；
 * - 关键字只允许中英文、数字与少量符号；
 * - 行数做上下限裁剪。
 *
 * 这是 M5"命令安全执行引擎"的雏形——真实项目里这类校验应该收敛到一个统一的地方，
 * 而不是散落在每个工具里。
 */
@Component
public class ContainerTools {

    private static final Logger log = LoggerFactory.getLogger(ContainerTools.class);

    /** 容器名白名单：以字母数字开头，只允许字母数字与 _ . - */
    private static final Pattern SAFE_CONTAINER = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$");

    /**
     * 搜索关键字白名单：中英文、数字、空格，以及 _ . - : 这几个安全符号。
     *
     * 为什么必须允许空格？真实的报错关键字往往带空格，例如
     * "Communications link failure"、"Connection is not available"。
     * 最初的规则不允许空格，导致 Agent 的合法搜索被拦住（这是实际测试发现的问题）。
     *
     * 而真正危险的字符——单引号、双引号、反引号、美元符、分号、竖线、& 等——
     * 一律不允许，因为它们能让内容从单引号字符串里"逃出来"执行别的命令。
     */
    private static final Pattern SAFE_KEYWORD =
            Pattern.compile("^[a-zA-Z0-9_.\\-:\\u4e00-\\u9fa5 ]{1,60}$");

    private final SshExecutor sshExecutor;
    private final ServerInfoService serverInfoService;

    public ContainerTools(SshExecutor sshExecutor, ServerInfoService serverInfoService) {
        this.sshExecutor = sshExecutor;
        this.serverInfoService = serverInfoService;
    }

    @Tool(description = "列出指定服务器上正在运行的 Docker 容器，返回容器名、镜像、状态和端口映射。"
            + "当需要排查'服务异常''CPU 被谁占用'这类问题时，先调用它看看有哪些容器在跑。")
    public String getContainerList(@ToolParam(description = "服务器 id") Long serverId) {
        long start = System.currentTimeMillis();
        try {
            ServerInfo server = requireServer(serverId);
            SshCommandResult result = sshExecutor.execute(server,
                    "LC_ALL=C docker ps --format '{{.Names}} | {{.Image}} | {{.Status}} | {{.Ports}}'", 20);

            String output = result.stdout().isBlank() ? "当前没有正在运行的容器。" : result.stdout().trim();
            ToolCallRecorder.record("get_container_list", "serverId=" + serverId,
                    output.lines().count() + " 个运行中的容器", System.currentTimeMillis() - start, true);
            return output;

        } catch (Exception e) {
            return fail("get_container_list", "serverId=" + serverId, e, start);
        }
    }

    @Tool(description = "读取指定容器最近的日志（默认最后 80 行）。当日志里出现错误时，"
            + "用它看完整的报错内容。")
    public String getContainerLogs(
            @ToolParam(description = "服务器 id") Long serverId,
            @ToolParam(description = "容器名称，例如 order-service，可从 getContainerList 获取") String containerName,
            @ToolParam(description = "读取最后多少行，1~500，默认 80", required = false) Integer lines) {
        long start = System.currentTimeMillis();
        try {
            ServerInfo server = requireServer(serverId);
            String name = requireContainer(containerName);
            int tail = clamp(lines, 80, 1, 500);

            SshCommandResult result = sshExecutor.execute(server,
                    "LC_ALL=C docker logs --tail " + tail + " " + name + " 2>&1", 30);

            String output = result.stdout().isBlank() ? "该容器没有输出日志。" : tailText(result.stdout(), 4000);
            ToolCallRecorder.record("get_container_logs", "serverId=" + serverId + ", container=" + name,
                    "读取最后 " + tail + " 行日志", System.currentTimeMillis() - start, true);
            return output;

        } catch (Exception e) {
            return fail("get_container_logs", "serverId=" + serverId + ", container=" + containerName, e, start);
        }
    }

    @Tool(description = "在指定容器的日志里按关键字搜索（不区分大小写），返回匹配的最近若干行。"
            + "例如查找 'timeout'、'ERROR'、'Exception' 快速定位问题。")
    public String searchLogs(
            @ToolParam(description = "服务器 id") Long serverId,
            @ToolParam(description = "容器名称") String containerName,
            @ToolParam(description = "搜索关键字，支持中英文、数字、空格和 _ . - : ，"
                    + "例如 timeout、Connection is not available") String keyword,
            @ToolParam(description = "最多返回多少行，1~100，默认 30", required = false) Integer maxLines) {
        long start = System.currentTimeMillis();
        try {
            ServerInfo server = requireServer(serverId);
            String name = requireContainer(containerName);
            String safeKeyword = requireKeyword(keyword);
            int limit = clamp(maxLines, 30, 1, 100);

            // 先从最近 1000 行里筛，再取最后 limit 行（避免历史日志淹没近期问题）
            String command = "LC_ALL=C docker logs --tail 1000 " + name + " 2>&1 | grep -i -- '"
                    + safeKeyword + "' | tail -n " + limit;
            SshCommandResult result = sshExecutor.execute(server, command, 30);

            String output = result.stdout().isBlank()
                    ? "最近 1000 行日志中没有匹配到关键字：" + safeKeyword
                    : result.stdout().trim();

            ToolCallRecorder.record("search_logs",
                    "container=" + name + ", keyword=" + safeKeyword,
                    output.lines().count() + " 行匹配", System.currentTimeMillis() - start, true);
            return output;

        } catch (Exception e) {
            return fail("search_logs",
                    "container=" + containerName + ", keyword=" + keyword, e, start);
        }
    }

    @Tool(description = "统计指定容器最近日志里各类错误与异常出现的次数，按次数从多到少排序。"
            + "当不确定问题是什么时，先用它看看主要错误有哪些，再决定深入查哪一类。")
    public String getErrorStatistics(
            @ToolParam(description = "服务器 id") Long serverId,
            @ToolParam(description = "容器名称") String containerName,
            @ToolParam(description = "统计最近多少行日志，100~2000，默认 800", required = false) Integer tailLines) {
        long start = System.currentTimeMillis();
        try {
            ServerInfo server = requireServer(serverId);
            String name = requireContainer(containerName);
            int tail = clamp(tailLines, 800, 100, 2000);

            String command = "LC_ALL=C docker logs --tail " + tail + " " + name + " 2>&1 "
                    + "| grep -Eo 'ERROR|WARN|Exception|Connection is not available|timed out|OutOfMemory' "
                    + "| sort | uniq -c | sort -rn | head -15";
            SshCommandResult result = sshExecutor.execute(server, command, 30);

            String output = result.stdout().isBlank()
                    ? "最近 " + tail + " 行日志中没有发现错误关键字。"
                    : "最近 " + tail + " 行日志的错误统计（次数 关键字）：\n" + result.stdout().trim();

            ToolCallRecorder.record("get_error_statistics", "container=" + name,
                    "统计最近 " + tail + " 行", System.currentTimeMillis() - start, true);
            return output;

        } catch (Exception e) {
            return fail("get_error_statistics", "container=" + containerName, e, start);
        }
    }

    /** 取服务器并校验 SSH 凭据是否配好 */
    private ServerInfo requireServer(Long serverId) {
        ServerInfo server = serverInfoService.getById(serverId);
        if (server == null) {
            throw new BizException("服务器不存在：id=" + serverId);
        }
        if (server.getUsername() == null || server.getUsername().isBlank()
                || server.getPassword() == null || server.getPassword().isBlank()) {
            throw new BizException("该服务器还没有配置 SSH 账号密码");
        }
        return server;
    }

    /** 容器名校验：不符合白名单一律拒绝，防止命令注入 */
    private String requireContainer(String containerName) {
        if (containerName == null || !SAFE_CONTAINER.matcher(containerName).matches()) {
            throw new BizException("容器名不合法：" + containerName);
        }
        return containerName;
    }

    /** 关键字校验：只允许安全字符，避免拼进 shell 后变成注入命令 */
    private String requireKeyword(String keyword) {
        if (keyword == null || !SAFE_KEYWORD.matcher(keyword).matches()) {
            throw new BizException("搜索关键字不合法（支持中英文、数字、空格和 _ . - :）：" + keyword);
        }
        return keyword;
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.min(Math.max(value, min), max);
    }

    /** 输出太长就截断，避免一次性塞给模型几万行日志（既费 token 又没意义） */
    private String tailText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text.trim();
        }
        return "（日志过长，仅展示最后部分）\n" + text.substring(text.length() - maxLength);
    }

    /** 统一处理失败：记录轨迹并返回可读的错误信息（不抛异常，让模型能解释失败原因） */
    private String fail(String tool, String arguments, Exception e, long start) {
        log.warn("工具 {} 执行失败：{}", tool, e.getMessage());
        ToolCallRecorder.record(tool, arguments, "执行失败：" + e.getMessage(),
                System.currentTimeMillis() - start, false);
        return "执行失败：" + e.getMessage();
    }
}
