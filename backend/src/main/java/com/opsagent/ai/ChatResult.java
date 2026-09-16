package com.opsagent.ai;

import java.util.List;

/**
 * 一次对话的结果。
 *
 * @param answer    模型给出的最终回答
 * @param toolCalls Agent 在回答过程中调用过的工具轨迹（按调用顺序）
 */
public record ChatResult(String answer, List<ToolCallRecord> toolCalls) {
}
