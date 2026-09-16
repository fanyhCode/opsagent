package com.opsagent.dto;

import com.opsagent.ai.ToolCallRecord;

import java.util.List;

/**
 * 一次对话的结果。
 *
 * @param sessionId 本次对话所属的会话 id（新建会话时前端要保存它）
 * @param answer    模型给出的最终回答
 * @param toolCalls Agent 在回答过程中调用过的工具轨迹（按调用顺序）
 */
public record ChatResult(Long sessionId, String answer, List<ToolCallRecord> toolCalls) {
}
