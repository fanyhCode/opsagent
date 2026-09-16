package com.opsagent.controller;

import com.opsagent.ai.AiChatService;
import com.opsagent.ai.AiUsageService;
import com.opsagent.common.Result;
import com.opsagent.dto.ChatRequest;
import com.opsagent.dto.AiUsageSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 助手接口。
 *
 * 当前是 M3.1：普通对话（验证模型通路）。
 * M3.2 会升级为工具调用：Agent 能自己决定去查 CPU、查进程、查日志。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService aiChatService;
    private final AiUsageService aiUsageService;

    public AiController(AiChatService aiChatService, AiUsageService aiUsageService) {
        this.aiChatService = aiChatService;
        this.aiUsageService = aiUsageService;
    }

    /**
     * POST /api/ai/chat
     * 请求体：{"message":"服务器 CPU 高怎么办？"}
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        String answer = aiChatService.chat(request.message());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", request.message());
        data.put("answer", answer);
        return Result.ok(data);
    }

    /**
     * GET /api/ai/usage
     * 返回 AI 用量（今日/累计的调用次数与 token）以及账户余额。
     * 余额低于阈值时，lowBalance 为 true，前端据此弹出提醒。
     */
    @GetMapping("/usage")
    public Result<AiUsageSummary> usage() {
        return Result.ok(aiUsageService.summary());
    }
}
