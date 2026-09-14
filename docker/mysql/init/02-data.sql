-- ============================================================
-- OpsAgent 初始数据脚本
-- 文件：docker/mysql/init/02-data.sql
--
-- 只放"系统初始化必需的数据"，业务数据不要写在这里。
-- 可以在已有数据库上重复执行：插入语句带 WHERE NOT EXISTS 判断，不会产生重复数据。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

-- 把自己这台 Ubuntu 虚拟机登记为被监控服务器。
-- 如果 IP 变了（在虚拟机执行 ip a 查看 ens33 的地址），这里和 application.yml 要同步修改。
INSERT INTO server (name, host, port, status, os, description)
SELECT 'ubuntu-vm',
       '192.168.169.129',
       22,
       'ONLINE',
       'Ubuntu 24.04 LTS',
       '本项目的被监控服务器（VMware 虚拟机）'
WHERE NOT EXISTS (SELECT 1 FROM server WHERE name = 'ubuntu-vm');
