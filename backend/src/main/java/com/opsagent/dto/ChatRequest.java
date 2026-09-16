package com.opsagent.dto;

/**
 * AI 对话请求参数。
 *
 * @param sessionId 会话 id。第一次提问时传 null，后端会自动新建一个会话并返回 id；
 *                  之后的提问带上同一个 sessionId，Agent 就能记住上下文。
 * @param message   用户的提问内容
 */
public record ChatRequest(Long sessionId, String message) {
}
