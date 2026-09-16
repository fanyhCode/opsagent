#!/usr/bin/env bash
# ============================================================
# 故障恢复脚本：让 order-service 回到正常状态
# 用法：在虚拟机里执行  bash ~/opsagent/scripts/clear-fault.sh
# ============================================================

set -euo pipefail

FLAG_FILE="${HOME}/opsagent-data/order-service/fault"

rm -f "${FLAG_FILE}"

echo "✅ 故障已清除：order-service 已恢复正常。"
echo
echo "--- 恢复后的容器资源占用 ---"
sleep 5
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' || true
