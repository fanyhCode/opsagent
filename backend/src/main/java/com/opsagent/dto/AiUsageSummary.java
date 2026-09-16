package com.opsagent.dto;

/**
 * AI 用量与余额汇总（前端展示用）。
 *
 * @param todayRequests   今日调用次数
 * @param todayTokens     今日消耗 token
 * @param totalRequests   累计调用次数
 * @param totalTokens     累计消耗 token
 * @param currency        余额币种，例如 CNY
 * @param balance         账户余额
 * @param available       账户是否可用（余额是否为负、是否被冻结等）
 * @param lowBalance      是否低于告警阈值
 * @param threshold       告警阈值
 * @param balanceError    查询余额失败时的原因（正常时为 null）
 * @param checkedAt       余额查询时间
 */
public record AiUsageSummary(
        long todayRequests,
        long todayTokens,
        long totalRequests,
        long totalTokens,
        String currency,
        String balance,
        boolean available,
        boolean lowBalance,
        String threshold,
        String balanceError,
        String checkedAt
) {
}
