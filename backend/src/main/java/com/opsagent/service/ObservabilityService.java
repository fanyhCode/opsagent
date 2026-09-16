package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.opsagent.ai.ToolCallRecord;
import com.opsagent.dto.ObservabilitySummary;
import com.opsagent.entity.AgentExecution;
import com.opsagent.entity.AiUsage;
import com.opsagent.entity.ChatMessage;
import com.opsagent.entity.CommandAudit;
import com.opsagent.mapper.AgentExecutionMapper;
import com.opsagent.mapper.AiUsageMapper;
import com.opsagent.mapper.ChatMessageMapper;
import com.opsagent.mapper.CommandAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 可观测性服务。
 *
 * 这里回答的是运维和面试里最容易被追问的问题：
 * "你怎么知道 Agent 工作得正常？调用了多少次工具？成功率多少？慢在哪？"
 *
 * 数据来源三张表：
 * - agent_execution：每一次工具调用（次数、耗时、成功与否）
 * - chat_message   ：用户提问次数（也就是"任务数"）
 * - ai_usage       ：token 消耗
 * - command_audit  ：操作审计（待确认、已执行、失败、被拒）
 */
@Service
public class ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    private final AgentExecutionMapper executionMapper;
    private final CommandAuditMapper auditMapper;
    private final ChatMessageMapper messageMapper;
    private final AiUsageMapper aiUsageMapper;

    public ObservabilityService(AgentExecutionMapper executionMapper,
                                CommandAuditMapper auditMapper,
                                ChatMessageMapper messageMapper,
                                AiUsageMapper aiUsageMapper) {
        this.executionMapper = executionMapper;
        this.auditMapper = auditMapper;
        this.messageMapper = messageMapper;
        this.aiUsageMapper = aiUsageMapper;
    }

    /**
     * 把一次对话里的工具调用全部落库。
     * 记流水失败不能影响对话，所以逐条 try/catch。
     */
    public void recordToolCalls(Long sessionId, Long userId, List<ToolCallRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (ToolCallRecord record : records) {
            try {
                AgentExecution row = new AgentExecution();
                row.setSessionId(sessionId);
                row.setUserId(userId);
                row.setToolName(record.tool());
                row.setArguments(truncate(record.arguments(), 255));
                row.setResultSummary(truncate(record.resultSummary(), 500));
                row.setDurationMs(record.durationMs());
                row.setSuccess(record.success() ? 1 : 0);
                row.setCreatedAt(LocalDateTime.now());
                executionMapper.insert(row);
            } catch (Exception e) {
                log.warn("记录工具调用失败：{}", e.getMessage());
            }
        }
    }

    /** 汇总可观测性指标 */
    public ObservabilitySummary summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 今日任务数 = 今日用户提问条数
        Long todayTasks = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, ChatMessage.ROLE_USER)
                .ge(ChatMessage::getCreatedAt, todayStart));

        Long todayToolCalls = executionMapper.selectCount(new LambdaQueryWrapper<AgentExecution>()
                .ge(AgentExecution::getCreatedAt, todayStart));
        Long totalToolCalls = executionMapper.selectCount(null);
        Long failCount = executionMapper.selectCount(new LambdaQueryWrapper<AgentExecution>()
                .eq(AgentExecution::getSuccess, 0));

        long total = nvl(totalToolCalls);
        long fails = nvl(failCount);
        double successRate = total == 0 ? 100.0 : round1((total - fails) * 100.0 / total);

        return new ObservabilitySummary(
                nvl(todayTasks),
                nvl(todayToolCalls),
                total,
                successRate,
                avgToolDuration(),
                sumTokens(todayStart),
                sumTokens(null),
                toolStats(),
                auditCount("approve_status", CommandAudit.APPROVE_PENDING),
                auditCount("execution_status", CommandAudit.EXEC_SUCCESS),
                auditCount("execution_status", CommandAudit.EXEC_FAILED),
                auditCount("approve_status", CommandAudit.APPROVE_BLOCKED)
        );
    }

    /** 工具调用平均耗时（毫秒） */
    private long avgToolDuration() {
        List<Map<String, Object>> rows = executionMapper.selectMaps(
                new QueryWrapper<AgentExecution>().select("IFNULL(ROUND(AVG(duration_ms)), 0) AS avg_ms"));
        return rows.isEmpty() ? 0 : toLong(rows.get(0).get("avg_ms"));
    }

    /** 按工具名分组统计：调用次数、平均耗时、失败次数 */
    private List<ObservabilitySummary.ToolStat> toolStats() {
        List<Map<String, Object>> rows = executionMapper.selectMaps(
                new QueryWrapper<AgentExecution>()
                        .select("tool_name AS tool",
                                "COUNT(*) AS cnt",
                                "IFNULL(ROUND(AVG(duration_ms)), 0) AS avg_ms",
                                "SUM(success = 0) AS fails")
                        .groupBy("tool_name")
                        .orderByDesc("cnt"));

        List<ObservabilitySummary.ToolStat> stats = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            stats.add(new ObservabilitySummary.ToolStat(
                    String.valueOf(row.get("tool")),
                    toLong(row.get("cnt")),
                    toLong(row.get("avg_ms")),
                    toLong(row.get("fails"))));
        }
        return stats;
    }

    /** 累计 token 消耗；since 为 null 表示累计全部 */
    private long sumTokens(LocalDateTime since) {
        QueryWrapper<AiUsage> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(SUM(total_tokens), 0) AS tokens");
        if (since != null) {
            wrapper.ge("created_at", since);
        }
        List<Map<String, Object>> rows = aiUsageMapper.selectMaps(wrapper);
        return rows.isEmpty() ? 0 : toLong(rows.get(0).get("tokens"));
    }

    /** 按某个状态字段统计操作数 */
    private long auditCount(String column, String value) {
        return nvl(auditMapper.selectCount(new QueryWrapper<CommandAudit>().eq(column, value)));
    }

    private long nvl(Long value) {
        return value == null ? 0 : value;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
