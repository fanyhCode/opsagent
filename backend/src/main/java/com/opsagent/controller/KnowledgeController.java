package com.opsagent.controller;

import com.opsagent.common.BizException;
import com.opsagent.common.Result;
import com.opsagent.dto.DocumentRequest;
import com.opsagent.dto.KnowledgeSearchResult;
import com.opsagent.entity.KnowledgeChunk;
import com.opsagent.entity.KnowledgeDocument;
import com.opsagent.security.UserContext;
import com.opsagent.service.KnowledgeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口。
 *
 * 注意：新增/删除文档、重建索引都是"写操作"，但属于平台自身的配置管理，
 * 不涉及在服务器上执行命令，所以没有走人工确认流程；
 * 真正危险的是"在目标服务器上执行命令"，那是命令安全执行引擎管的范围。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public Result<List<KnowledgeDocument>> documents() {
        requireLogin();
        return Result.ok(knowledgeService.listDocuments());
    }

    /** 新增文档 */
    @PostMapping("/documents")
    public Result<KnowledgeDocument> addDocument(@RequestBody DocumentRequest request) {
        requireLogin();
        return Result.ok("文档已添加，记得重建索引后才会被检索到",
                knowledgeService.addDocument(request.title(), request.category(), request.content()));
    }

    /** 删除文档（连同它的切片） */
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        requireLogin();
        knowledgeService.deleteDocument(id);
        return Result.ok("文档已删除", null);
    }

    /** 切片列表（可按文档过滤） */
    @GetMapping("/chunks")
    public Result<List<KnowledgeChunk>> chunks(@RequestParam(required = false) Long documentId) {
        requireLogin();
        return Result.ok(knowledgeService.listChunks(documentId));
    }

    /** 重建索引：重新切片 + 向量化 */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        requireLogin();
        return Result.ok("索引重建完成", knowledgeService.reindex());
    }

    /** 向量接口自检 */
    @GetMapping("/test-embedding")
    public Result<Map<String, Object>> testEmbedding() {
        requireLogin();
        return Result.ok(knowledgeService.testEmbedding());
    }

    /** 检索测试（前端可以用来演示检索效果） */
    @GetMapping("/search")
    public Result<List<KnowledgeSearchResult>> search(@RequestParam("q") String query,
                                                      @RequestParam(required = false) Integer topK) {
        requireLogin();
        return Result.ok(knowledgeService.search(query, topK));
    }

    private void requireLogin() {
        if (UserContext.get() == null) {
            throw new BizException(401, "未登录");
        }
    }
}
