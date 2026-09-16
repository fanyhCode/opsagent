-- ============================================================
-- 数据库升级脚本：新增操作审计表 command_audit（M5.1 命令安全执行引擎）
-- 执行方式：在 Navicat 里打开本文件并运行。可重复执行。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

CREATE TABLE IF NOT EXISTS command_audit
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT                DEFAULT NULL COMMENT '发起用户 id（来自哪个账号的会话）',
    session_id       BIGINT                DEFAULT NULL COMMENT '来源会话 id（可追溯到具体对话）',
    server_id        BIGINT       NOT NULL COMMENT '目标服务器 id',
    operation        VARCHAR(64)  NOT NULL COMMENT '操作名，例如 restart_container',
    target           VARCHAR(128)          DEFAULT NULL COMMENT '操作对象，例如容器名',
    command_preview  VARCHAR(255) NOT NULL COMMENT '将要执行的命令（给人看的预览）',
    risk_level       VARCHAR(16)  NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH',
    approve_status   VARCHAR(16)  NOT NULL COMMENT '审批状态：PENDING/APPROVED/REJECTED/BLOCKED',
    execution_status VARCHAR(16)  NOT NULL DEFAULT 'NOT_EXECUTED' COMMENT '执行状态：NOT_EXECUTED/SUCCESS/FAILED',
    result           TEXT                  DEFAULT NULL COMMENT '执行输出或失败原因',
    reason           VARCHAR(255)          DEFAULT NULL COMMENT 'Agent 给出的操作理由',
    approved_by      BIGINT                DEFAULT NULL COMMENT '确认人用户 id',
    execution_time   BIGINT       NOT NULL DEFAULT 0 COMMENT '执行耗时（毫秒）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提出时间',
    approved_at      DATETIME              DEFAULT NULL COMMENT '确认/拒绝时间',
    executed_at      DATETIME              DEFAULT NULL COMMENT '执行时间',
    PRIMARY KEY (id),
    KEY idx_status_created (approve_status, created_at),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='命令审计表';
