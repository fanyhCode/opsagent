-- ============================================================
-- 数据库升级脚本：新增知识库表 + 内置故障案例（M5.3 RAG）
-- 执行方式：在 Navicat 里打开本文件并运行。可重复执行（INSERT IGNORE + 唯一标题）。
-- ============================================================

SET NAMES utf8mb4;

USE opsagent;

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

-- 内置故障案例（唯一标题 + INSERT IGNORE，重复执行不会重复插入）
INSERT IGNORE INTO knowledge_document (title, category, content) VALUES
('HikariCP 连接池耗尽导致接口超时', 'MYSQL',
'【典型现象】\n应用接口大面积超时，日志里出现大量 "HikariPool-1 - Connection is not available, request timed out after 30000ms"，同时 CPU 升高、线程数上涨。\n\n【排查步骤】\n1. 看应用日志：搜索 "Connection is not available"，确认是否是连接池拿不到连接。\n2. 看数据库侧连接数：执行 show status like ''Threads_connected''; 与 show variables like ''max_connections''; 对比是否接近上限。\n3. 看是否有慢 SQL：show processlist 查看长时间运行的语句，或开启慢查询日志分析。\n4. 看连接池配置：maximumPoolSize、connectionTimeout、maxLifetime 是否合理。\n\n【常见根因】\n1. 连接泄漏：代码里拿了连接没关闭（没有 try-with-resources 或事务未提交）。\n2. 慢 SQL 占满连接：某条查询执行时间过长，连接迟迟不归还。\n3. 池子太小：并发量上涨后 maximumPoolSize 不够用。\n4. maxLifetime 大于数据库 wait_timeout：连接被数据库单方面断开，池里留下死连接，报 "No operations allowed after connection closed"。\n\n【处置建议】\n1. 紧急止血：重启故障服务可以快速恢复，但必须配合根因修复，否则会复发。\n2. 修复连接泄漏，给所有连接使用加 try-with-resources。\n3. 优化慢 SQL，必要时加索引。\n4. 调整 maxLifetime 使其小于数据库的 wait_timeout。\n5. 增加连接池监控指标（活跃连接数、等待线程数）。'),

('Linux CPU 使用率飙高的排查方法', 'LINUX',
'【默认排查顺序：从整体到细节】\n1. 看整体负载与 CPU 状态分布：top -b -n1 | head -20。注意区分 us（用户态）、sy（内核态）、wa（IO 等待）。若 wa 高，说明瓶颈在磁盘 IO 而不是 CPU 计算。\n2. 定位高 CPU 进程：ps -eo pid,ppid,pcpu,pmem,stat,comm --sort=-pcpu | head -15。\n3. 定位进程内的高 CPU 线程：top -H -p <PID>，再把线程 id 转成十六进制后用 jstack <PID> | grep -A 30 <hex> 看 Java 线程栈。\n4. 若是容器内进程，用 docker stats 确认是哪个容器，再看 docker logs。\n\n【常见根因】\n1. 业务流量上涨导致的正常高负载。\n2. 死循环或无限重试（例如下游超时后疯狂重试）。\n3. 频繁 GC（JVM 堆不足或内存泄漏）。\n4. 连接池耗尽导致线程阻塞重试，间接拉高 CPU。\n5. 定时任务在同一时刻集中执行。\n\n【注意】排查命令都是只读的，可以安全执行；涉及重启服务的操作必须先确认影响面。'),

('Docker 容器被 OOM Kill 的原因与排查', 'DOCKER',
'【典型现象】容器突然退出，docker ps 里消失；docker inspect <容器> 看到 OOMKilled=true；应用日志在退出前没有明显报错。\n\n【排查步骤】\n1. docker inspect <容器> --format ''{{.State.OOMKilled}} {{.State.ExitCode}}'' 确认是否被 OOM。\n2. docker stats --no-stream 看容器内存占用与限制。\n3. 查看宿主机内核日志：dmesg | grep -i "killed process"，能看到被杀的进程与内存信息。\n4. 确认容器内存限制：docker inspect <容器> --format ''{{.HostConfig.Memory}}''。\n\n【常见根因】\n1. 容器内存限制过小（例如限制 512MB，而 JVM 堆就配了 1G）。\n2. 应用内存泄漏，缓存无限增长。\n3. JVM 参数未考虑容器限制：Java 8 早期版本不识别 cgroup 限制，需要显式设置 -Xmx。\n4. 大文件或多线程任务导致瞬时内存峰值。\n\n【处置建议】\n1. 合理设置容器内存限制与 JVM 堆大小（堆一般不超过容器内存的 75%）。\n2. 排查内存泄漏，加上内存使用监控与告警。\n3. 使用 -XX:MaxRAMPercentage 让 JVM 自动适配容器内存。'),

('JVM Full GC 频繁的常见原因与排查', 'JVM',
'【典型现象】接口响应变慢、CPU 升高，GC 日志显示 Full GC 频率明显增加（例如 10 分钟内几十次），堆内存使用曲线呈锯齿状且谷底越来越高。\n\n【排查命令】\n1. jstat -gcutil <PID> 1000 10 —— 观察老年代使用率（O）与 Full GC 次数（FGC）。\n2. jmap -histo:live <PID> | head -30 —— 看哪些对象占内存最多。\n3. jmap -dump:format=b,file=/tmp/heap.hprof <PID> —— 导出堆快照，用 MAT 分析大对象与引用链。\n4. jstack <PID> 看是否有大量线程阻塞。\n\n【常见根因】\n1. 堆内存不足：业务数据量增长，老年代长期接近上限。\n2. 内存泄漏：集合只增不减（例如本地缓存没有过期策略）。\n3. 大对象频繁创建：一次查询加载过多数据。\n4. 显式调用 System.gc() 或使用了不合适的垃圾回收器。\n\n【处置建议】\n1. 先用 jstat 定位是"内存不够"还是"泄漏"，两者的处理方式完全不同。\n2. 内存不足：适当调大堆并调整新生代比例；泄漏：修复代码。\n3. 引入监控（堆使用率、GC 次数、GC 耗时），设置阈值告警。'),

('磁盘空间不足的排查与清理', 'LINUX',
'【排查步骤】\n1. df -h 看哪个分区满了（注意 / 与 /var 常是重灾区）。\n2. du -sh /* 2>/dev/null | sort -rh | head -10 逐层定位大目录。\n3. 检查常见增长点：docker 镜像与容器日志（/var/lib/docker）、应用日志目录、系统 journal 日志。\n4. lsof +L1 查看有没有被删除但仍被进程占用的文件（空间不会释放）。\n\n【常见根因】\n1. 应用日志没有轮转，单个文件涨到几十 GB。\n2. Docker 日志未限制大小（默认 json-file 无限增长）。\n3. 大量无用镜像、悬空卷、容器层堆积。\n4. 数据库 binlog 或备份文件堆积。\n\n【清理建议】\n1. 配置日志轮转（logrotate）与 Docker 日志上限（max-size / max-file）。\n2. docker system prune 清理无用镜像与停止的容器（执行前确认影响面）。\n3. 谨慎执行删除操作：清理生产环境文件属于高风险操作，必须先备份并人工确认。');
