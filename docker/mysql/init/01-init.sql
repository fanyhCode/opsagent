-- OpsAgent 数据库初始化脚本
-- 这个文件被挂载到 MySQL 容器的 /docker-entrypoint-initdb.d 目录，
-- 容器第一次启动（数据卷为空）时会自动执行，用来建库、建表和写入初始数据。

-- 强制当前会话使用 utf8mb4。
-- 为什么必须写这一行：mysql 客户端连接时的默认字符集可能不是 utf8mb4，
-- 那样脚本里的中文（UTF-8 字节）会被当成 Latin-1 字符存进数据库，
-- 查询出来就会变成 "æœ¬é¡¹ç›®" 这种乱码（学名叫双重编码）。
SET NAMES utf8mb4;

-- 建库：字符集用 utf8mb4，才能正确存储中文和 emoji
CREATE DATABASE IF NOT EXISTS opsagent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE opsagent;

-- 服务器信息表：记录被平台纳管的 Linux 服务器
CREATE TABLE IF NOT EXISTS server
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '服务器名称',
    host        VARCHAR(64)  NOT NULL COMMENT '主机地址（IP 或域名）',
    port        INT          NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
    status      VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态：ONLINE/OFFLINE/UNKNOWN',
    os          VARCHAR(64)  DEFAULT NULL COMMENT '操作系统版本',
    description VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器信息表';

-- 初始数据：把我们的 Ubuntu 虚拟机登记进来，作为被监控服务器
INSERT INTO server (name, host, port, status, os, description)
VALUES ('ubuntu-vm', '192.168.169.129', 22, 'ONLINE', 'Ubuntu 24.04 LTS', '本项目的被监控服务器（VMware 虚拟机）');
