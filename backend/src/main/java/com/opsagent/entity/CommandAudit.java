package com.opsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 操作审计记录，对应 command_audit 表。
 * 一条记录 = 一次"待确认的操作"，从提出、审批到执行的全过程都在这里。
 */
@TableName("command_audit")
public class CommandAudit {

    /** 审批状态常量 */
    public static final String APPROVE_PENDING = "PENDING";
    public static final String APPROVE_APPROVED = "APPROVED";
    public static final String APPROVE_REJECTED = "REJECTED";
    public static final String APPROVE_BLOCKED = "BLOCKED";

    /** 执行状态常量 */
    public static final String EXEC_NOT_EXECUTED = "NOT_EXECUTED";
    public static final String EXEC_SUCCESS = "SUCCESS";
    public static final String EXEC_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long serverId;

    private String operation;

    private String target;

    private String commandPreview;

    private String riskLevel;

    private String approveStatus;

    private String executionStatus;

    private String result;

    private String reason;

    private Long approvedBy;

    private Long executionTime;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime executedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getCommandPreview() {
        return commandPreview;
    }

    public void setCommandPreview(String commandPreview) {
        this.commandPreview = commandPreview;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getApproveStatus() {
        return approveStatus;
    }

    public void setApproveStatus(String approveStatus) {
        this.approveStatus = approveStatus;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
