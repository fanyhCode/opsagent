package com.opsagent.ops;

/**
 * 操作风险等级。
 *
 * LOW    —— 只读查询，可以直接执行（例如看指标、看日志）；
 * MEDIUM —— 会影响服务状态但通常可恢复，必须人工确认（例如重启容器）；
 * HIGH   —— 可能造成数据丢失或服务不可用，**禁止 Agent 执行**（例如删除文件、关机）。
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
