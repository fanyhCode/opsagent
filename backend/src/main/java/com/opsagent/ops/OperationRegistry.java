package com.opsagent.ops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 操作注册表：整个平台"允许执行什么"的唯一事实来源。
 *
 * 这是命令安全执行引擎的核心思想——**白名单**：
 * 只有登记在这里的操作才可能被执行，其它一律拒绝。
 * 相比"黑名单"（列出禁止的命令），白名单不会因为想漏一条命令而被绕过。
 *
 * 当前白名单：
 *   restart_container  MEDIUM  需确认  docker restart <容器名>
 *   （HIGH 类操作登记为 allowed=false，用于把"我本来就不允许"这件事显式化）
 */
public final class OperationRegistry {

    /** 容器名白名单：以字母数字开头，只允许字母数字与 _ . - */
    public static final Pattern SAFE_TARGET = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$");

    private static final Map<String, OperationDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        // ---------- 允许执行（需人工确认）----------
        register(new OperationDefinition(
                "restart_container",
                "重启指定容器",
                RiskLevel.MEDIUM,
                true,
                true,
                "docker restart %s",
                "容器名称，例如 order-service"
        ));

        // ---------- 明确禁止（Agent 不得执行）----------
        register(new OperationDefinition(
                "delete_file",
                "删除服务器上的文件",
                RiskLevel.HIGH,
                false,
                true,
                "rm -f %s",
                "文件路径"
        ));
        register(new OperationDefinition(
                "kill_process",
                "强制结束进程",
                RiskLevel.HIGH,
                false,
                true,
                "kill -9 %s",
                "进程 PID"
        ));
        register(new OperationDefinition(
                "shutdown_server",
                "关闭服务器",
                RiskLevel.HIGH,
                false,
                true,
                "shutdown -h now",
                "无参数"
        ));
        register(new OperationDefinition(
                "modify_firewall",
                "修改防火墙规则",
                RiskLevel.HIGH,
                false,
                true,
                "iptables %s",
                "防火墙规则"
        ));
    }

    private OperationRegistry() {
    }

    private static void register(OperationDefinition definition) {
        DEFINITIONS.put(definition.name(), definition);
    }

    /** 按名字取定义，不存在返回 null（调用方据此拒绝） */
    public static OperationDefinition find(String name) {
        return name == null ? null : DEFINITIONS.get(name.trim());
    }

    /** 全部操作定义，用于展示给模型或前端 */
    public static List<OperationDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }
}
