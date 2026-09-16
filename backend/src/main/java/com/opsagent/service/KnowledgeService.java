package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsagent.common.BizException;
import com.opsagent.dto.KnowledgeSearchResult;
import com.opsagent.entity.KnowledgeChunk;
import com.opsagent.entity.KnowledgeDocument;
import com.opsagent.mapper.KnowledgeChunkMapper;
import com.opsagent.mapper.KnowledgeDocumentMapper;
import com.opsagent.rag.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障知识库服务（RAG 的检索侧）。
 *
 * 一条完整链路：
 * 文档 -> 切片 chunk -> 向量化 embedding -> 存库；
 * 用户问题 -> 向量化 -> 与所有切片算余弦相似度 -> 取最相关的 Top-K -> 交给模型参考。
 *
 * 为什么把向量存 MySQL、在 Java 里算相似度？
 * 因为知识库只有几十到几百条切片，一次全量比较的耗时可忽略；
 * 而引入 pgvector 或 Milvus 要多养一套数据库、多一层运维成本。
 * 数据量涨到十万级再迁移即可，这是按规模选型，而不是按流行度选型。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    private final int chunkSize;
    private final int chunkOverlap;
    private final int defaultTopK;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                            KnowledgeChunkMapper chunkMapper,
                            EmbeddingClient embeddingClient,
                            ObjectMapper objectMapper,
                            @Value("${opsagent.rag.chunk-size:400}") int chunkSize,
                            @Value("${opsagent.rag.chunk-overlap:80}") int chunkOverlap,
                            @Value("${opsagent.rag.top-k:3}") int defaultTopK) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.defaultTopK = defaultTopK;
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .orderByAsc(KnowledgeDocument::getId));
    }

    public KnowledgeDocument addDocument(String title, String category, String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BizException("标题和内容都不能为空");
        }
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title.trim());
        document.setCategory(category == null || category.isBlank() ? "GENERAL" : category.trim().toUpperCase());
        document.setContent(content);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);
        return document;
    }

    public void deleteDocument(Long id) {
        chunkMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("document_id", id));
        documentMapper.deleteById(id);
    }

    public List<KnowledgeChunk> listChunks(Long documentId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<KnowledgeChunk>()
                .orderByAsc(KnowledgeChunk::getDocumentId, KnowledgeChunk::getChunkIndex);
        if (documentId != null) {
            wrapper.eq(KnowledgeChunk::getDocumentId, documentId);
        }
        return chunkMapper.selectList(wrapper);
    }

    /**
     * 重建索引：把所有文档重新切片并向量化。
     * 首次启用知识库、修改过文档、或者更换向量模型之后都要执行一次。
     */
    public Map<String, Object> reindex() {
        List<KnowledgeDocument> documents = listDocuments();
        if (documents.isEmpty()) {
            throw new BizException("知识库里还没有文档");
        }

        // 清空旧切片：换模型后向量维度会变，必须重建
        chunkMapper.delete(new QueryWrapper<KnowledgeChunk>());

        int chunkCount = 0;
        int dimension = 0;
        for (KnowledgeDocument document : documents) {
            List<String> pieces = splitIntoChunks(document.getContent());
            if (pieces.isEmpty()) {
                continue;
            }
            List<double[]> vectors = embeddingClient.embed(pieces);
            for (int i = 0; i < pieces.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(pieces.get(i));
                chunk.setEmbedding(toJson(vectors.get(i)));
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);

                chunkCount++;
                dimension = vectors.get(i).length;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", documents.size());
        result.put("chunks", chunkCount);
        result.put("dimension", dimension);
        result.put("model", embeddingClient.getModel());
        log.info("知识库重建完成：{} 篇文档 -> {} 个切片，维度 {}",
                documents.size(), chunkCount, dimension);
        return result;
    }

    /** 检索最相关的切片 */
    public List<KnowledgeSearchResult> search(String query, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new BizException("请输入检索内容");
        }
        int limit = topK == null ? defaultTopK : Math.min(Math.max(topK, 1), 10);

        List<KnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .isNotNull(KnowledgeChunk::getEmbedding));
        if (chunks.isEmpty()) {
            return List.of();
        }

        double[] queryVector = embeddingClient.embedOne(query);

        // 文档标题先查出来，避免每条结果都查一次库
        Map<Long, KnowledgeDocument> documents = new LinkedHashMap<>();
        for (KnowledgeDocument document : listDocuments()) {
            documents.put(document.getId(), document);
        }

        List<KnowledgeSearchResult> results = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            double score = cosine(queryVector, fromJson(chunk.getEmbedding()));
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            results.add(new KnowledgeSearchResult(
                    chunk.getDocumentId(),
                    document == null ? "未知文档" : document.getTitle(),
                    document == null ? "GENERAL" : document.getCategory(),
                    chunk.getChunkIndex(),
                    Math.round(score * 1000) / 1000.0,
                    chunk.getContent()));
        }

        results.sort(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed());
        return results.subList(0, Math.min(limit, results.size()));
    }

    /** 自检：把一句话向量化，返回模型、维度与耗时，用来确认接口配置正确 */
    public Map<String, Object> testEmbedding() {
        long start = System.currentTimeMillis();
        double[] vector = embeddingClient.embedOne("这是一段用于验证向量化接口的测试文本");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", embeddingClient.isConfigured() ? "已配置" : "未配置");
        result.put("model", embeddingClient.getModel());
        result.put("dimension", vector.length);
        result.put("elapsedMs", System.currentTimeMillis() - start);
        result.put("firstThreeValues", List.of(vector[0], vector[1], vector[2]));
        return result;
    }

    /**
     * 按段落切片：
     * 优先在段落边界切，避免把一句话从中间截断；相邻切片保留一段重叠，
     * 防止跨段落的关键信息被切开后两边都检索不到。
     */
    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        StringBuilder buffer = new StringBuilder();
        for (String rawParagraph : content.split("\\n\\s*\\n")) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            if (paragraph.length() > chunkSize) {
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                int step = Math.max(1, chunkSize - chunkOverlap);
                for (int i = 0; i < paragraph.length(); i += step) {
                    chunks.add(paragraph.substring(i, Math.min(paragraph.length(), i + chunkSize)));
                }
                continue;
            }

            if (buffer.length() + paragraph.length() > chunkSize && buffer.length() > 0) {
                String previous = buffer.toString().trim();
                chunks.add(previous);
                buffer.setLength(0);
                if (chunkOverlap > 0 && previous.length() > chunkOverlap) {
                    buffer.append(previous.substring(previous.length() - chunkOverlap)).append("\n\n");
                }
            }
            buffer.append(paragraph).append("\n\n");
        }

        if (buffer.length() > 0) {
            chunks.add(buffer.toString().trim());
        }
        return chunks;
    }

    /** 余弦相似度：衡量两个向量方向的一致性，值域 0~1 */
    private double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new BizException("向量序列化失败：" + e.getMessage());
        }
    }

    private double[] fromJson(String json) {
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception e) {
            log.warn("向量反序列化失败：{}", e.getMessage());
            return new double[0];
        }
    }
}
