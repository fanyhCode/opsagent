package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opsagent.common.BizException;
import com.opsagent.entity.CommandAudit;
import com.opsagent.entity.ServerInfo;
import com.opsagent.mapper.CommandAuditMapper;
import com.opsagent.ops.OperationDefinition;
import com.opsagent.ops.OperationRegistry;
import com.opsagent.ops.RiskLevel;
import com.opsagent.security.LoginUser;
import com.opsagent.ssh.SshCommandResult;
import com.opsagent.ssh.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 命令安全执行引擎。
 *
 * 整套流程是"人在回路"（Human-in-the-loop）：
 *
 *   Agent 提出操作 propose()  —— 只落库，不执行，状态 PENDING
 *        ↓ （前端展示待确认列表）
 *   人工确认 approve()        —— 校验角色 + 校验目标参数 + 渲染命令 + 通过 SSH 执行
 *   人工拒绝 reject()         —— 只改状态，不执行
 *
 * 四道安全闸门：
 * 1. **白名单**：操作名必须在 OperationRegistry 里登记，否则直接拒绝；
 * 2. **风险分级**：HIGH 类操作登记为 allowed=false，永远不执行；
 * 3. **参数校验**：目标（容器名等）必须匹配白名单正则，防止命令注入；
 * 4. **人工确认 + 角色校验**：只有 ADMIN/OPERATOR 能确认，且每次执行都留痕。
 */
@Service
public class OperationService {

    private static final Logger log = LoggerFactory.getLogger(OperationService.class);

    private final CommandAuditMapper auditMapper;
    private final ServerInfoService serverInfoService;
    private final SshExecutor sshExecutor;

    public OperationService(CommandAuditMapper auditMapper,
                            ServerInfoService serverInfoService,
                            SshExecutor sshExecutor) {
        this.auditMapper = auditMapper;
        this.serverInfoService = serverInfoService;
        this.sshExecutor = sshExecutor;
    }

    /**
     * 提出一个操作（Agent 调用）。
     * 注意：**只做登记，绝不执行**。
     */
    public CommandAudit propose(Long userId, Long sessionId, Long serverId,
                                String operation, String target, String reason) {
        OperationDefinition definition = OperationRegistry.find(operation);

        CommandAudit audit = new CommandAudit();
        audit.setUserId(userId);
        audit.setSessionId(sessionId);
        audit.setServerId(serverId == null ? 1L : serverId);
        audit.setOperation(operation == null ? "" : operation);
        audit.setTarget(target);
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        audit.setExecutionStatus(CommandAudit.EXEC_NOT_EXECUTED);
        audit.setExecutionTime(0L);

        // 闸门 1：不在白名单 → 直接阻止，但保留痕迹
        if (definition == null) {
            audit.setRiskLevel(RiskLevel.HIGH.name());
            audit.setApproveStatus(CommandAudit.APPROVE_BLOCKED);
            audit.setCommandPreview("（该操作不在命令白名单中，未生成命令）");
            audit.setResult("操作不在白名单中，平台已拒绝。");
            auditMapper.insert(audit);
            log.warn("拒绝未登记的操作：{}", operation);
            return audit;
        }

        // 闸门 3 的第一半：参数格式校验（防止命令注入）
        String targetValue = definition.commandTemplate().contains("%s") ? target : null;
        if (definition.commandTemplate().contains("%s")
                && (targetValue == null || !OperationRegistry.SAFE_TARGET.matcher(targetValue).matches())) {
            audit.setRiskLevel(definition.riskLevel().name());
            audit.setApproveStatus(CommandAudit.APPROVE_BLOCKED);
            audit.setCommandPreview(definition.commandTemplate());
            audit.setResult("操作目标不合法：" + target);
            auditMapper.insert(audit);
            log.warn("拒绝参数非法的操作：{} target={}", operation, target);
            return audit;
        }

        String preview = definition.commandTemplate().contains("%s")
                ? String.format(definition.commandTemplate(), targetValue)
                : definition.commandTemplate();

        audit.setRiskLevel(definition.riskLevel().name());
        audit.setCommandPreview(preview);

        // 闸门 2：HIGH 风险操作登记为禁止
        if (!definition.allowed()) {
            audit.setApproveStatus(CommandAudit.APPROVE_BLOCKED);
            audit.setResult("该操作风险等级为 " + definition.riskLevel() + "，平台禁止 Agent 执行。");
            auditMapper.insert(audit);
            log.warn("拒绝高风险操作：{}", operation);
            return audit;
        }

        // 允许执行的操作：进入待确认队列
        audit.setApproveStatus(CommandAudit.APPROVE_PENDING);
        auditMapper.insert(audit);
        log.info("操作已提交待确认：id={} {} {}", audit.getId(), operation, target);
        return audit;
    }

    /**
     * 人工确认并执行。
     * 闸门 4：角色校验 + 状态校验，然后才真正执行命令。
     */
    public CommandAudit approve(Long auditId, LoginUser approver) {
        requireOperatorRole(approver);
        CommandAudit audit = requireAudit(auditId);
        if (!CommandAudit.APPROVE_PENDING.equals(audit.getApproveStatus())) {
            throw new BizException("该操作当前状态为 " + audit.getApproveStatus() + "，不能再次确认");
        }

        OperationDefinition definition = OperationRegistry.find(audit.getOperation());
        if (definition == null || !definition.allowed()) {
            throw new BizException("该操作不在可执行白名单中");
        }

        audit.setApproveStatus(CommandAudit.APPROVE_APPROVED);
        audit.setApprovedBy(approver.id());
        audit.setApprovedAt(LocalDateTime.now());
        execute(audit, definition);
        return audit;
    }

    /** 人工拒绝：只改状态，不执行 */
    public CommandAudit reject(Long auditId, LoginUser approver) {
        requireOperatorRole(approver);
        CommandAudit audit = requireAudit(auditId);
        if (!CommandAudit.APPROVE_PENDING.equals(audit.getApproveStatus())) {
            throw new BizException("该操作当前状态为 " + audit.getApproveStatus() + "，不能拒绝");
        }
        audit.setApproveStatus(CommandAudit.APPROVE_REJECTED);
        audit.setApprovedBy(approver.id());
        audit.setApprovedAt(LocalDateTime.now());
        audit.setResult("人工拒绝执行");
        auditMapper.updateById(audit);
        log.info("操作被拒绝：id={} by={}", auditId, approver.username());
        return audit;
    }

    /** 待确认列表（所有登录用户可见，因为确认人可能不是发起人） */
    public List<CommandAudit> listPending() {
        return auditMapper.selectList(new LambdaQueryWrapper<CommandAudit>()
                .eq(CommandAudit::getApproveStatus, CommandAudit.APPROVE_PENDING)
                .orderByDesc(CommandAudit::getCreatedAt));
    }

    /** 最近的审计记录（操作历史） */
    public List<CommandAudit> listAudits(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return auditMapper.selectList(new LambdaQueryWrapper<CommandAudit>()
                .orderByDesc(CommandAudit::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 真正执行命令：渲染模板 → SSH 执行 → 记录结果与耗时 */
    private void execute(CommandAudit audit, OperationDefinition definition) {
        ServerInfo server = serverInfoService.getById(audit.getServerId());
        if (server == null) {
            throw new BizException("服务器不存在：id=" + audit.getServerId());
        }

        String command = definition.commandTemplate().contains("%s")
                ? String.format(definition.commandTemplate(), audit.getTarget())
                : definition.commandTemplate();

        long start = System.currentTimeMillis();
        try {
            SshCommandResult result = sshExecutor.execute(server, "LC_ALL=C " + command, 60);
            long cost = System.currentTimeMillis() - start;

            audit.setExecutionTime(cost);
            audit.setExecutedAt(LocalDateTime.now());
            if (result.exitStatus() == 0) {
                audit.setExecutionStatus(CommandAudit.EXEC_SUCCESS);
                audit.setResult(trim(result.stdout().isBlank() ? "执行成功（无输出）" : result.stdout()));
            } else {
                audit.setExecutionStatus(CommandAudit.EXEC_FAILED);
                audit.setResult("命令退出码 " + result.exitStatus() + "：" + trim(result.stderr()));
            }
            log.info("操作执行完成：id={} command={} status={} 耗时 {} ms",
                    audit.getId(), command, audit.getExecutionStatus(), cost);

        } catch (Exception e) {
            audit.setExecutionTime(System.currentTimeMillis() - start);
            audit.setExecutedAt(LocalDateTime.now());
            audit.setExecutionStatus(CommandAudit.EXEC_FAILED);
            audit.setResult("执行失败：" + e.getMessage());
            log.warn("操作执行失败：id={} reason={}", audit.getId(), e.getMessage());
        }
        auditMapper.updateById(audit);
    }

    /** 只有 ADMIN / OPERATOR 能确认或拒绝操作，VIEWER 只能看 */
    private void requireOperatorRole(LoginUser user) {
        if (user == null) {
            throw new BizException(401, "未登录");
        }
        String role = user.role() == null ? "" : user.role().toUpperCase();
        if (!"ADMIN".equals(role) && !"OPERATOR".equals(role)) {
            throw new BizException(403, "当前角色（" + user.role() + "）无权确认执行操作，需要 ADMIN 或 OPERATOR");
        }
    }

    private CommandAudit requireAudit(Long auditId) {
        CommandAudit audit = auditMapper.selectById(auditId);
        if (audit == null) {
            throw new BizException("操作记录不存在：id=" + auditId);
        }
        return audit;
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 2000 ? text.trim() : text.substring(0, 2000) + "…";
    }
}
