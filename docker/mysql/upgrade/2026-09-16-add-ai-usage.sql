-- ============================================================
-- 数据库升级脚本：新增 ai_usage 表（AI 调用记录）
-- 执行方式：在 Navicat 里打开本文件并运行。
-- 使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

CREATE TABLE IF NOT EXISTS ai_usage
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT               DEFAULT NULL COMMENT '调用者用户 id',
    model             VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模型名称，例如 deepseek-chat',
    prompt_tokens     INT         NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    completion_tokens INT         NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    total_tokens      INT         NOT NULL DEFAULT 0 COMMENT '总 token 数',
    duration_ms       BIGINT      NOT NULL DEFAULT 0 COMMENT '本次调用耗时（毫秒）',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 调用记录表';
