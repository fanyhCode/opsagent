package com.opsagent.ai;

import com.opsagent.entity.CommandAudit;
import com.opsagent.ops.OperationDefinition;
import com.opsagent.ops.OperationRegistry;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import com.opsagent.service.OperationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * 操作类工具（会让服务器状态发生变化，因此全部走"人工确认"流程）。
 *
 * 关键设计：**Agent 只能"提出"操作，不能"执行"操作**。
 * proposeRestartContainer 只是往审计表里写一条 PENDING 记录，
 * 真正执行要等用户在界面上点"确认执行"——这就是 Human-in-the-loop。
 *
 * 为什么不做成"通用执行命令"的工具？因为那等于给模型开了一个任意 shell 的入口，
 * 白名单、风险分级、人工确认三套机制都会被绕过。工具越"收敛"，系统越安全。
 */
@Component
public class OperationTools {

    private final OperationService operationService;

    public OperationTools(OperationService operationService) {
        this.operationService = operationService;
    }

    @Tool(description = "提交一个【重启容器】操作，交给用户人工确认。"
            + "注意：调用本工具不会立即重启容器，只是把操作提交到待确认列表；"
            + "只有在用户在界面上点击确认之后，命令才会真正执行。"
            + "适用场景：容器已经确认出现异常、且重启是合理的恢复手段时（例如连接池异常导致服务不可用）。")
    public String proposeRestartContainer(
            @ToolParam(description = "服务器 id") Long serverId,
            @ToolParam(description = "要重启的容器名称，例如 order-service") String containerName,
            @ToolParam(description = "提出该操作的理由，会展示给确认人看") String reason) {

        long start = System.currentTimeMillis();
        try {
            LoginUser user = UserContext.get();
            Long sessionId = ChatContextHolder.get();

            CommandAudit audit = operationService.propose(
                    user == null ? null : user.id(),
                    sessionId,
                    serverId,
                    "restart_container",
                    containerName,
                    reason);

            String summary = "已提交待人工确认（操作 id=" + audit.getId()
                    + "，风险等级 " + audit.getRiskLevel() + "）";
            ToolCallRecorder.record("propose_restart_container",
                    "container=" + containerName, summary,
                    System.currentTimeMillis() - start, true);

            if (CommandAudit.APPROVE_PENDING.equals(audit.getApproveStatus())) {
                return """
                        已提交人工确认，操作 id=%d。
                        待执行命令：%s
                        风险等级：%s
                        请告知用户：需要在界面的"待确认操作"里点击确认后才会真正执行。"""
                        .formatted(audit.getId(), audit.getCommandPreview(), audit.getRiskLevel());
            }
            return "操作未进入待确认队列。" + audit.getResult();

        } catch (Exception e) {
            ToolCallRecorder.record("propose_restart_container",
                    "container=" + containerName, "提交失败：" + e.getMessage(),
                    System.currentTimeMillis() - start, false);
            return "提交操作失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询平台支持哪些操作、各自的风险等级，以及哪些操作被禁止。"
            + "当你不确定某个操作是否被允许时，先调用它确认。")
    public String listSupportedOperations() {
        long start = System.currentTimeMillis();
        StringJoiner joiner = new StringJoiner("\n");
        for (OperationDefinition definition : OperationRegistry.all()) {
            joiner.add("%s（%s）—— %s，%s".formatted(
                    definition.name(),
                    definition.riskLevel(),
                    definition.description(),
                    definition.allowed() ? "可执行，但需要人工确认" : "禁止执行"));
        }
        String result = joiner.toString();
        ToolCallRecorder.record("list_supported_operations", "无参数",
                OperationRegistry.all().size() + " 个操作定义",
                System.currentTimeMillis() - start, true);
        return result;
    }

    @Tool(description = "查询当前所有等待人工确认的操作及其状态。"
            + "当用户问'有什么待确认的操作''刚才那个操作执行了吗'时调用。")
    public String listPendingOperations() {
        long start = System.currentTimeMillis();
        List<CommandAudit> pending = operationService.listPending();
        if (pending.isEmpty()) {
            ToolCallRecorder.record("list_pending_operations", "无参数", "没有待确认操作",
                    System.currentTimeMillis() - start, true);
            return "当前没有待人工确认的操作。";
        }

        StringJoiner joiner = new StringJoiner("\n");
        for (CommandAudit audit : pending) {
            joiner.add("id=%d, 操作=%s, 目标=%s, 命令=%s, 风险=%s, 提出时间=%s"
                    .formatted(audit.getId(), audit.getOperation(), audit.getTarget(),
                            audit.getCommandPreview(), audit.getRiskLevel(), audit.getCreatedAt()));
        }
        String result = joiner.toString();
        ToolCallRecorder.record("list_pending_operations", "无参数",
                pending.size() + " 条待确认", System.currentTimeMillis() - start, true);
        return result;
    }
}
