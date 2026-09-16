package com.opsagent.controller;

import com.opsagent.ai.AiChatService;
import com.opsagent.ai.AiUsageService;
import com.opsagent.common.BizException;
import com.opsagent.common.Result;
import com.opsagent.dto.AiUsageSummary;
import com.opsagent.dto.ChatRequest;
import com.opsagent.dto.ChatResult;
import com.opsagent.entity.ChatMessage;
import com.opsagent.entity.ChatSession;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import com.opsagent.service.ChatSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助手接口。
 *
 * 包含三类能力：
 * 1. 对话（带工具调用与多轮记忆）；
 * 2. 会话管理（列表、消息、删除）；
 * 3. 用量与余额查询。
 *
 * 所有接口都要求登录，并且**只能操作自己的会话**（越权校验在 Service 层做）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService aiChatService;
    private final AiUsageService aiUsageService;
    private final ChatSessionService chatSessionService;

    public AiController(AiChatService aiChatService,
                        AiUsageService aiUsageService,
                        ChatSessionService chatSessionService) {
        this.aiChatService = aiChatService;
        this.aiUsageService = aiUsageService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * POST /api/ai/chat
     * 请求体：{"sessionId": 1, "message": "服务器现在正常吗？"}
     * sessionId 传 null 表示新开一个会话。
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        LoginUser currentUser = requireLogin();
        ChatResult result = aiChatService.chat(request.sessionId(), request.message(), currentUser.id());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", result.sessionId());
        data.put("question", request.message());
        data.put("answer", result.answer());
        // Agent 的工具调用轨迹，前端据此展示"它做了什么"
        data.put("toolCalls", result.toolCalls());
        return Result.ok(data);
    }

    /** GET /api/ai/sessions —— 当前用户的会话列表 */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions() {
        return Result.ok(chatSessionService.listByUser(requireLogin().id()));
    }

    /** GET /api/ai/sessions/{id}/messages —— 某个会话的全部消息 */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        LoginUser currentUser = requireLogin();
        // 先校验归属，再取消息，防止越权读取别人的对话
        chatSessionService.getOwned(id, currentUser.id());
        return Result.ok(chatSessionService.listMessages(id));
    }

    /** DELETE /api/ai/sessions/{id} —— 删除会话及其消息 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatSessionService.delete(id, requireLogin().id());
        return Result.ok("会话已删除", null);
    }

    /**
     * GET /api/ai/usage
     * 返回 AI 用量（今日/累计的调用次数与 token）以及账户余额。
     */
    @GetMapping("/usage")
    public Result<AiUsageSummary> usage() {
        return Result.ok(aiUsageService.summary());
    }

    /** 取当前登录用户，没登录直接拒绝（拦截器已保证，这里再兜一层） */
    private LoginUser requireLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(401, "未登录");
        }
        return user;
    }
}
