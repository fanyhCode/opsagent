package com.opsagent.ai;

/**
 * 一次工具调用的记录，用于在前端展示"Agent 到底做了什么"。
 *
 * @param tool          工具名，例如 get_server_overview
 * @param arguments     调用参数（人类可读的字符串）
 * @param resultSummary 结果摘要（截断后的文本，用于界面展示，不是给模型看的原文）
 * @param durationMs    本次工具执行耗时（毫秒）
 * @param success       是否执行成功
 */
public record ToolCallRecord(String tool, String arguments, String resultSummary,
                            long durationMs, boolean success) {
}
