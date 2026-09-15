-- ============================================================
-- 数据库升级脚本：给 server 表补充 SSH 登录字段（M2）
-- 执行方式：在 Navicat 里打开本文件并运行（针对已有数据库）。
--
-- 关于升级脚本的约定：
--   新建数据库时会执行 docker/mysql/init 下的初始化脚本，
--   而已经跑起来的数据库需要单独执行 upgrade 目录下的升级脚本。
--   文件名以日期开头，方便看出先后顺序。
--
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--       所以这个脚本只需执行一次；如果提示"列已存在"，说明已经执行过了。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

ALTER TABLE server
    ADD COLUMN username VARCHAR(64) DEFAULT NULL COMMENT 'SSH 登录用户名' AFTER port,
    ADD COLUMN password VARCHAR(128) DEFAULT NULL COMMENT 'SSH 登录密码（开发阶段明文，M5 阶段改为加密存储或密钥登录）' AFTER username;
