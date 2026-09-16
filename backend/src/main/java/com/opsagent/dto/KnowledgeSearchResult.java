package com.opsagent.dto;

/**
 * 一条知识库检索结果。
 *
 * @param documentId 命中的文档 id
 * @param title      文档标题（Agent 引用时用得上）
 * @param category   文档分类
 * @param chunkIndex 命中的切片序号
 * @param score      相似度（0~1，越大越相关）
 * @param content    切片内容
 */
public record KnowledgeSearchResult(
        Long documentId,
        String title,
        String category,
        Integer chunkIndex,
        double score,
        String content
) {
}
