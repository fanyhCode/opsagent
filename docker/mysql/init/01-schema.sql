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

-- ------------------------------------------------------------
-- 表 5：chat_session —— 对话会话表
-- 一次"连续对话"就是一条会话记录，标题取自用户的第一句话。
-- 会话的意义：让 Agent 记得上下文（"它内存为什么高"里的"它"指的是谁）。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 表 6：chat_message —— 会话消息表
-- 保存每一条用户提问与 Agent 回答，同时记录这次回答调用了哪些工具。
-- 这张表同时也是"Agent 可观测性"的一部分：出问题时可以回放整段对话。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 表 7：command_audit —— 操作审计表（命令安全执行引擎的落地点）
-- 作用：Agent 提出的每一个写操作都先写进这张表，状态为 PENDING（待确认）；
-- 人工确认后才真正执行，执行结果与耗时同样记录在案。
-- 这样"AI 提了什么、谁批的、执行结果如何"全程可追溯——这就是全链路审计。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 表 8：agent_execution —— Agent 工具调用记录表
-- 作用：Agent 每一次工具调用都记一条（工具名、参数、耗时、成功与否）。
-- 这是"Agent 可观测性"的数据基础：能统计出哪个工具被调用最多、
-- 平均耗时多少、失败率多高——出问题时可以回放整个执行链路。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 表 9：knowledge_document —— 故障知识库文档表
-- 存的是"故障案例/运维手册"原文，例如"HikariCP 连接池耗尽怎么排查"。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_document
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title      VARCHAR(200) NOT NULL COMMENT '文档标题',
    category   VARCHAR(32)  NOT NULL DEFAULT 'GENERAL' COMMENT '分类：LINUX/DOCKER/JVM/MYSQL/REDIS/GENERAL',
    content    TEXT         NOT NULL COMMENT '文档正文',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_title (title)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='故障知识库文档表';

-- ------------------------------------------------------------
-- 表 10：knowledge_chunk —— 文档切片表（RAG 的检索单元）
-- 一篇文档会被切成若干段，每段单独向量化后存 embedding 字段。
-- 为什么要切片？整篇文档太长，检索时粒度太粗；切成段后能精确定位到
-- "哪一段话"和用户的问题最相关。
--
-- embedding 存的是 JSON 数组字符串（如 [0.12,-0.03,...]）。
-- 规模不大时，在 Java 里算余弦相似度完全够用，省掉一整套向量数据库的部署成本；
-- 数据量上到十万级再迁移到 pgvector / Milvus。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_chunk
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id BIGINT      NOT NULL COMMENT '所属文档 id',
    chunk_index INT         NOT NULL DEFAULT 0 COMMENT '该文档内的第几段',
    content     TEXT        NOT NULL COMMENT '切片内容',
    embedding   LONGTEXT             DEFAULT NULL COMMENT '向量（JSON 数组），未索引时为 NULL',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_document (document_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识库文档切片表';
