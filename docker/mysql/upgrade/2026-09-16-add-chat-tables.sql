-- ============================================================
-- 数据库升级脚本：新增会话表与消息表（M3.3 多轮对话记忆）
-- 执行方式：在 Navicat 里打开本文件并运行。可重复执行。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

CREATE TABLE IF NOT EXISTS chat_session
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NOT NULL COMMENT '所属用户 id',
    title      VARCHAR(128) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活动时间',
    PRIMARY KEY (id),
    KEY idx_user_updated (user_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='对话会话表';

CREATE TABLE IF NOT EXISTS chat_message
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT      NOT NULL COMMENT '所属会话 id',
    role       VARCHAR(16) NOT NULL COMMENT '角色：USER 用户 / ASSISTANT 助手',
    content    TEXT        NOT NULL COMMENT '消息内容',
    tool_calls TEXT                 DEFAULT NULL COMMENT '本次回答的工具调用轨迹（JSON 数组）',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_created (session_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='会话消息表';
