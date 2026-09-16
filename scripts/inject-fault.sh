#!/usr/bin/env bash
# ============================================================
# 故障注入脚本：让 order-service 进入"数据库连接超时 + CPU 飙高"状态
#
# 用途：演示 Agent 的自动诊断能力。
# 用法：在虚拟机里执行  bash ~/opsagent/scripts/inject-fault.sh
# ============================================================

set -euo pipefail

FLAG_DIR="${HOME}/opsagent-data/order-service"
FLAG_FILE="${FLAG_DIR}/fault"

if ! docker ps --format '{{.Names}}' | grep -q '^order-service$'; then
  echo "⚠️  没有找到 order-service 容器，请先按 docker/demo/order-service/README.md 构建并启动它。"
  exit 1
fi

mkdir -p "${FLAG_DIR}"
touch "${FLAG_FILE}"

echo "✅ 故障已注入：order-service 开始输出数据库连接超时错误，并占用 CPU。"
echo "   等待 20 秒后观察效果，然后可以让 Agent 诊断：'服务器为什么变慢了？'"
echo
echo "--- 当前容器资源占用 ---"
sleep 8
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'
