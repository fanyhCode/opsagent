# OpsAgent —— 基于 AI Agent 的智能 Linux 运维与故障诊断平台

> 让 AI 从"回答问题"升级到：理解服务器状态 → 调用工具 → 分析结果 → 定位故障 → 给出方案 → 在安全边界内执行。

## 一、项目简介

OpsAgent 是一个 AI Agent 驱动的 Linux 运维与故障诊断平台。

它不是一个普通的 AI 聊天机器人：当服务器出现 CPU 飙高、内存泄漏、容器异常退出、日志大量报错等故障时，Agent 会根据当前上下文**自主决定**调用哪些工具（Linux / Docker / JVM / 日志 / 知识库），获取实时数据并进行多轮推理，最终输出故障根因与处置建议。

平台同时设计了 **Linux 命令安全执行引擎**：所有命令必须经过白名单校验、参数校验和风险分级，高风险操作必须经过人工确认才能执行，每一次 Agent 工具调用与命令执行都会写入审计日志。

**一句话总结**：一个"会自己排查问题、但不会乱来"的运维 AI。

## 二、核心功能

| 能力 | 说明 |
| --- | --- |
| AI Agent 工具调用 | Agent 依据故障上下文动态选择工具，支持多轮调用与结果回喂，并设置最大轮次 / 超时 / Token 上限防止失控 |
| Linux 命令安全执行引擎 | 命令白名单 + 参数校验 + 风险分级（LOW / MEDIUM / HIGH）+ 人工确认，禁止 Agent 执行高危命令 |
| 多源数据关联诊断 | 将 CPU / 内存 / 磁盘指标、进程信息、JVM 状态、Docker 容器、应用日志关联分析，从"异常指标"定位到"具体服务" |
| RAG 故障知识库 | 向量检索历史故障案例，结合实时监控数据辅助根因分析 |
| 服务器监控与告警 | 定时采集指标、展示趋势、按阈值触发告警并自动触发 Agent 诊断 |
| Agent 可观测性 | 记录任务、工具调用、参数、结果、耗时、Token、风险等级，实现全链路可追踪、可审计 |
| 权限体系 | Spring Security + JWT + RBAC（ADMIN / OPERATOR / VIEWER） |
| 容器化部署 | Docker Compose + Nginx，部署在 Ubuntu Linux 环境 |

## 三、技术栈

| 模块 | 技术 |
| --- | --- |
| 语言 | JDK 17 |
| 后端 | Spring Boot 3、Spring AI、MyBatis-Plus |
| AI | DeepSeek（LLM）、本地 bge-small-zh（Embedding） |
| 前端 | Vue3、Element Plus、ECharts |
| 数据库 | MySQL 8、pgvector（向量检索） |
| 缓存 | Redis |
| 权限 | Spring Security + JWT |
| 运维环境 | Ubuntu 24.04、Docker、Docker Compose、Nginx |
| 日志 | Logback |

## 四、系统架构

```text
                    Vue3 Dashboard
                          │
                          ▼
                  Nginx / Spring Boot
                          │
        ┌─────────────────┼──────────────────┐
        ▼                 ▼                  ▼
   监控告警模块       AI Agent          知识库(RAG)
                         │                  │
                 ┌─── Tool Calling ───┐      │
                 ▼        ▼     ▼     ▼      ▼
               Linux   Docker  JVM   日志  pgvector
                 │        │     │     │
                 └────────┴─────┴─────┘
                          │
                          ▼
                  被监控 Linux 服务器
                 （虚拟机 + Docker 模拟服务）
```

## 五、目录结构

```text
opsagent
├── backend        # Spring Boot 后端（Agent、监控、工具、权限、审计）
├── frontend       # Vue3 前端（Dashboard、Agent 对话、审计等页面）
├── docker         # Dockerfile、docker-compose、初始化脚本
├── docs           # 项目规划、学习笔记、面试问答
├── scripts        # 模拟故障脚本、部署脚本
└── README.md
```

## 六、快速部署

整套平台（后端 + 前端 + 数据库 + 模拟被诊断服务）可以用 Docker Compose 一键启动。

### 1. 准备环境变量

```bash
cd ~/opsagent
cp .env.example .env
vi .env            # 填入 DEEPSEEK_API_KEY 与 DASHSCOPE_API_KEY
mkdir -p ~/opsagent-data/order-service
```

`.env` 已被 `.gitignore` 排除，不会提交到仓库。

### 2. 启动

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
docker compose --env-file .env -f docker/docker-compose.yml ps
```

### 3. 访问

```text
http://<虚拟机IP>          # 控制台（Nginx 统一入口，前端静态资源 + /api 反向代理）
docker stats              # 查看各容器资源占用
```

### 4. 一键演示

```bash
bash ~/opsagent/scripts/demo.sh
```

脚本会自动完成：注入故障 → 登录 → Agent 自主诊断 → 打印工具调用轨迹与结论 → 恢复故障 → 回放操作审计。

## 七、开发路线

- [ ] M0 环境准备：Ubuntu 虚拟机、Docker、Git 与 GitHub 打通
- [ ] M1 基础平台：Spring Boot + Vue3 + MySQL + 登录注册 + JWT + RBAC
- [ ] M2 Linux 监控：指标采集与 Dashboard 展示
- [ ] M3 Agent 核心：DeepSeek 接入 + Tool Calling + Agent Loop + 对话页
- [ ] M4 故障工具：Docker / 日志 / JVM 工具 + 模拟故障环境
- [ ] M5 RAG + 安全 + 审计：pgvector 知识库、命令安全执行引擎、人工确认、审计
- [ ] M6 部署与包装：Docker Compose 部署、Nginx、演示脚本、面试文档

## 八、项目状态

🚧 开发中
