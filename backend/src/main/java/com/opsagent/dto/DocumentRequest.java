package com.opsagent.dto;

/**
 * 新增知识库文档的请求参数。
 */
public record DocumentRequest(String title, String category, String content) {
}
