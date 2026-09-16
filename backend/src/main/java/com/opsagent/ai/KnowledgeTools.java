package com.opsagent.ai;

import com.opsagent.dto.KnowledgeSearchResult;
import com.opsagent.service.KnowledgeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * 知识库检索工具（RAG 的"检索"入口）。
 *
 * 它的价值在于：让 Agent 的回答不再只依赖模型自己的记忆，
 * 而是能引用我们整理的故障案例与处置手册——这就是 RAG 的意义。
 *
 * 注意这里只做"检索"，不做"写入"：往知识库里加文档是人的操作，
 * 不应该由模型自主决定（否则模型可能往知识库里塞入不准确的内容）。
 */
@Component
public class KnowledgeTools {

    private final KnowledgeService knowledgeService;

    public KnowledgeTools(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool(description = "在故障知识库中检索历史故障案例与处置手册，返回最相关的片段和相似度。"
            + "适用场景：需要判断故障根因、需要给出标准处置步骤时。"
            + "例如用户问'连接池耗尽怎么处理''Full GC 频繁怎么办'。"
            + "拿到结果后请在回答里标明引用了哪篇案例。")
    public String searchKnowledge(
            @ToolParam(description = "检索用的关键词或问题描述，例如 连接池耗尽、Full GC 频繁") String query) {

        long start = System.currentTimeMillis();
        try {
            List<KnowledgeSearchResult> results = knowledgeService.search(query, null);
            if (results.isEmpty()) {
                ToolCallRecorder.record("search_knowledge", "query=" + query,
                        "知识库中没有匹配内容（可能还没建立索引）",
                        System.currentTimeMillis() - start, true);
                return "知识库中没有检索到相关内容（可能尚未建立索引，需要管理员执行一次重建索引）。";
            }

            StringJoiner joiner = new StringJoiner("\n\n");
            for (KnowledgeSearchResult item : results) {
                joiner.add("【案例】%s（分类：%s，相似度：%s）\n%s"
                        .formatted(item.title(), item.category(), item.score(), item.content()));
            }
            String result = joiner.toString();

            ToolCallRecorder.record("search_knowledge", "query=" + query,
                    "命中 " + results.size() + " 条，最高相似度 " + results.get(0).score(),
                    System.currentTimeMillis() - start, true);
            return result;

        } catch (Exception e) {
            ToolCallRecorder.record("search_knowledge", "query=" + query,
                    "检索失败：" + e.getMessage(), System.currentTimeMillis() - start, false);
            return "知识库检索失败：" + e.getMessage();
        }
    }
}
