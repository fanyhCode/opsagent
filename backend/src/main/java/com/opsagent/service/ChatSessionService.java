package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opsagent.common.BizException;
import com.opsagent.entity.ChatMessage;
import com.opsagent.entity.ChatSession;
import com.opsagent.mapper.ChatMessageMapper;
import com.opsagent.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话服务：管理对话会话与消息，是"多轮对话记忆"的落地点。
 *
 * 两个关键设计：
 *
 * 1. **越权防护**：所有按 id 取会话的地方都要校验"这个会话是不是当前用户的"。
 *    否则用户 A 只要把 URL 里的会话 id 改成 B 的，就能读到别人的对话内容
 *    （这类漏洞叫 IDOR，越权访问，是 Web 安全里最常见的问题之一）。
 *
 * 2. **历史条数上限**：喂给模型的历史消息不能无限增长。
 *    一方面 token 会随对话轮数线性上涨（花钱），另一方面模型上下文也有长度限制。
 *    所以只带最近 N 条——这是"控制成本"最直接的手段。
 */
@Service
public class ChatSessionService {

    /** 带进模型的历史消息条数上限（不含本次提问） */
    public static final int MAX_HISTORY_MESSAGES = 20;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatSessionService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    /** 创建新会话：标题先用占位，等用户发了第一句话再自动命名 */
    public ChatSession create(Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新会话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    /** 某用户的会话列表，最近活动的排前面 */
    public List<ChatSession> listByUser(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt));
    }

    /**
     * 按 id 取会话，并校验归属。
     * 不是自己的会话一律报"不存在或无权访问"——不告诉对方"这个会话存在但不是你的"，
     * 避免泄露信息。
     */
    public ChatSession getOwned(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BizException("会话不存在或无权访问");
        }
        return session;
    }

    /** 会话的全部消息，按时间正序 */
    public List<ChatMessage> listMessages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt, ChatMessage::getId));
    }

    /** 取最近 N 条历史消息（结果按时间正序，便于直接拼进提示词） */
    public List<ChatMessage> recentMessages(Long sessionId, int limit) {
        List<ChatMessage> latest = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + Math.max(1, limit)));

        List<ChatMessage> ordered = new ArrayList<>(latest);
        Collections.reverse(ordered);
        return ordered;
    }

    /** 追加一条消息 */
    public ChatMessage append(Long sessionId, String role, String content, String toolCallsJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setToolCalls(toolCallsJson);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    /**
     * 用用户的第一句话生成会话标题。
     * 这里只改内存里的对象，不直接写库——由调用方统一 touch() 保存，避免同一行更新两次。
     */
    public void autoTitle(ChatSession session, String firstMessage) {
        if (session.getTitle() != null && !"新会话".equals(session.getTitle())) {
            return;
        }
        String title = firstMessage == null ? "新会话" : firstMessage.replaceAll("\\s+", " ").trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "…";
        }
        session.setTitle(title.isEmpty() ? "新会话" : title);
    }

    /** 更新会话的最后活动时间（决定会话列表排序） */
    public void touch(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /** 删除会话及其全部消息 */
    @Transactional
    public void delete(Long sessionId, Long userId) {
        getOwned(sessionId, userId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }
}
