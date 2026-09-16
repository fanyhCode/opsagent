package com.opsagent.dto;

import java.util.List;

/**
 * Agent 可观测性统计。
 *
 * @param todayTasks        今日对话轮次（用户提问次数）
 * @param todayToolCalls    今日工具调用次数
 * @param totalToolCalls    累计工具调用次数
 * @param successRate       工具调用成功率（0~100）
 * @param avgDurationMs     工具调用平均耗时
 * @param todayTokens       今日消耗 token
 * @param totalTokens       累计消耗 token
 * @param toolStats         各工具的调用情况
 * @param operationPending  待人工确认的操作数
 * @param operationExecuted 已执行的操作数
 * @param operationFailed   执行失败的操作数
 * @param operationBlocked  被平台拒绝的操作数
 */
public record ObservabilitySummary(
        long todayTasks,
        long todayToolCalls,
        long totalToolCalls,
        double successRate,
        long avgDurationMs,
        long todayTokens,
        long totalTokens,
        List<ToolStat> toolStats,
        long operationPending,
        long operationExecuted,
        long operationFailed,
        long operationBlocked
) {

    /**
     * 单个工具的统计。
     *
     * @param tool          工具名
     * @param count         调用次数
     * @param avgDurationMs 平均耗时
     * @param failCount     失败次数
     */
    public record ToolStat(String tool, long count, long avgDurationMs, long failCount) {
    }
}
