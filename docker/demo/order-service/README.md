# 模拟 order-service（故障演示用）

这不是真实业务服务，只是一个**用来产生日志和 CPU 负载的模拟容器**，
目的是让 Agent 有真实的排查对象：容器、日志、进程、指标。

## 构建与启动（在虚拟机里执行）

```bash
cd ~/opsagent/docker/demo/order-service

docker build -t opsagent/order-service:1.0 .

mkdir -p ~/opsagent-data/order-service

docker run -d --name order-service \
  --restart unless-stopped \
  -v ~/opsagent-data/order-service:/app/flag \
  opsagent/order-service:1.0
```

## 状态切换

| 操作 | 命令（虚拟机里执行） |
| --- | --- |
| 注入故障（CPU 飙高 + 刷数据库连接超时日志） | `touch ~/opsagent-data/order-service/fault` |
| 恢复正常 | `rm -f ~/opsagent-data/order-service/fault` |

也可以用仓库里的脚本：`bash scripts/inject-fault.sh` 和 `bash scripts/clear-fault.sh`。

## 观察方式

```bash
docker ps                      # 容器是否在运行
docker stats --no-stream       # 容器 CPU / 内存占用
docker logs --tail 20 order-service   # 看日志
```

故障模式下应当能看到：

- `docker stats` 里 order-service 的 CPU 明显上升；
- `docker logs` 里大量 `HikariPool-1 - Connection is not available` 错误；
- 平台监控页的 CPU 曲线同步抬高。
