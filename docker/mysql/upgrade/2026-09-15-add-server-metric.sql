-- ============================================================
-- 数据库升级脚本：新增 server_metric 指标采集表（M2）
-- 执行方式：在 Navicat 里打开本文件并运行。
-- 脚本使用 CREATE TABLE IF NOT EXISTS，重复执行不会报错。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

CREATE TABLE IF NOT EXISTS server_metric
(
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_id    BIGINT        NOT NULL COMMENT '服务器 id，对应 server.id',
    cpu_usage    DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT 'CPU 使用率（%）',
    memory_usage DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '内存使用率（%）',
    disk_usage   DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '根分区使用率（%）',
    load_average DECIMAL(6, 2) NOT NULL DEFAULT 0 COMMENT '1 分钟平均负载',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    PRIMARY KEY (id),
    KEY idx_server_time (server_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器指标采集表';
