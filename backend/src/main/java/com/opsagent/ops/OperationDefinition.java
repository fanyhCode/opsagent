package com.opsagent.ops;

/**
 * 一个受支持的操作定义（命令白名单里的一条）。
 *
 * @param name             操作名，模型和前端都用它，例如 restart_container
 * @param description      给人和模型看的中文说明
 * @param riskLevel        风险等级
 * @param allowed          是否允许执行（HIGH 类一律 false，属于"明确禁止"）
 * @param requiresApproval 是否需要人工确认
 * @param commandTemplate  命令模板，%s 会被替换成 target；例如 "docker restart %s"
 * @param targetHint       目标参数的格式说明，用于校验和提示
 */
public record OperationDefinition(
        String name,
        String description,
        RiskLevel riskLevel,
        boolean allowed,
        boolean requiresApproval,
        String commandTemplate,
        String targetHint
) {
}
