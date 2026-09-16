-- ============================================================
-- 数据库升级脚本：新增 agent_execution 表（M5.2 Agent 可观测性）
-- 执行方式：在 Navicat 里打开本文件并运行。可重复执行。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

CREATE TABLE IF NOT EXISTS agent_execution
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id     BIGINT                DEFAULT NULL COMMENT '所属会话 id',
    user_id        BIGINT                DEFAULT NULL COMMENT '调用用户 id',
    tool_name      VARCHAR(64)  NOT NULL COMMENT '工具名称',
    arguments      VARCHAR(255)          DEFAULT NULL COMMENT '调用参数',
    result_summary VARCHAR(500)          DEFAULT NULL COMMENT '结果摘要',
    duration_ms    BIGINT       NOT NULL DEFAULT 0 COMMENT '执行耗时（毫秒）',
    success        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功，0 失败',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_created (created_at),
    KEY idx_tool (tool_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 工具调用记录表';
