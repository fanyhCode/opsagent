-- ============================================================
-- OpsAgent 数据库表结构（DDL 建表脚本）
-- 文件：docker/mysql/init/01-schema.sql
--
-- 使用说明：
-- 1. MySQL 容器第一次启动（数据卷为空）时会自动执行本目录下的所有 .sql 文件，
--    顺序按文件名排序：先 01-schema.sql 建表，再 02-data.sql 写入初始数据；
-- 2. 本文件全部使用 CREATE ... IF NOT EXISTS，可以重复执行，不会破坏已有数据；
-- 3. 给已有数据库补建表时，用 Navicat 打开本文件并运行即可（详见 docs/测试手册.md）。
--
-- 命名约定：
--   表名用小写下划线（server、sys_user），主键统一叫 id，
--   时间字段统一用 created_at / updated_at，
--   每张表都写 COMMENT，每个字段都写注释，方便别人看懂表结构。
-- ============================================================

-- 让本次会话按 utf8mb4 解析脚本内容，避免中文被当成 Latin-1 造成乱码
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 数据库
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS opsagent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE opsagent;

-- ------------------------------------------------------------
-- 表 1：server —— 服务器信息表
-- 作用：记录平台纳管的每一台 Linux 服务器。
-- 后续的指标采集（M2）、Agent 诊断（M3）都以这张表里的记录为操作对象。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '服务器名称，例如 ubuntu-vm',
    host        VARCHAR(64)  NOT NULL COMMENT '主机地址（IP 或域名）',
    port        INT          NOT NULL DEFAULT 22 COMMENT 'SSH 端口，默认 22',
    username    VARCHAR(64)           DEFAULT NULL COMMENT 'SSH 登录用户名',
    password    VARCHAR(128)          DEFAULT NULL COMMENT 'SSH 登录密码（开发阶段明文，M5 阶段改为加密存储或密钥登录）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态：ONLINE 在线 / OFFLINE 离线 / UNKNOWN 未知',
    os          VARCHAR(64)           DEFAULT NULL COMMENT '操作系统版本，例如 Ubuntu 24.04 LTS',
    description VARCHAR(255)          DEFAULT NULL COMMENT '备注说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器信息表';

-- ------------------------------------------------------------
-- 表 2：sys_user —— 系统用户表
-- 作用：平台的登录账号，配合角色字段实现 RBAC 权限控制。
-- 注意：password 存的是 BCrypt 哈希值（$2a$10$ 开头，60 个字符），绝不明文存储。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64)  NOT NULL COMMENT '登录用户名，全局唯一',
    password   VARCHAR(100) NOT NULL COMMENT '密码哈希（BCrypt），不存明文',
    nickname   VARCHAR(64)           DEFAULT NULL COMMENT '昵称，用于界面展示',
    role       VARCHAR(16)  NOT NULL DEFAULT 'VIEWER' COMMENT '角色：ADMIN 管理员 / OPERATOR 运维 / VIEWER 只读',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- ------------------------------------------------------------
-- 表 3：server_metric —— 服务器指标采集表
-- 作用：定时任务每隔一段时间采集一次 CPU/内存/磁盘/负载并写入本表，
-- 形成历史曲线数据（监控页面的趋势图就是查这张表）。
--
-- 为什么建 (server_id, created_at) 联合索引？
-- 因为趋势图的查询永远是"某台服务器、最近一段时间"，联合索引能让这类查询走索引而不是全表扫描。
-- 这是时序数据表的标准做法。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 表 4：ai_usage —— AI 调用记录表
-- 作用：每次调用大模型都记一条，记录消耗的 token 数量与耗时。
-- 有了它，前端就能显示"今日用量/累计用量"，也为后面的 Agent 可观测性打基础。
-- ------------------------------------------------------------
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
