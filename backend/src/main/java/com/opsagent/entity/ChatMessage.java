package com.opsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 会话中的一条消息，对应 chat_message 表。
 */
@TableName("chat_message")
public class ChatMessage {

    /** 角色常量：用户 */
    public static final String ROLE_USER = "USER";

    /** 角色常量：助手 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** USER 或 ASSISTANT */
    private String role;

    private String content;

    /**
     * 本次回答的工具调用轨迹，存 JSON 字符串。
     * 为什么不建一张单独的表？因为轨迹只跟着这条消息一起读写，
     * 没有独立查询需求，用 JSON 存更简单（这也是"避免过度设计"的取舍）。
     */
    private String toolCalls;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
