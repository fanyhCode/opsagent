#!/usr/bin/env bash
# ============================================================
# OpsAgent 一键演示脚本
#
# 完整复现设计文档里的招牌场景：
#   注入故障 -> 登录平台 -> 让 Agent 自主诊断 -> 打印工具调用轨迹与结论
#   -> 恢复故障 -> 回放操作审计记录
#
# 用法（在虚拟机里执行）：
#   bash ~/opsagent/scripts/demo.sh
#
# 可覆盖的环境变量：
#   BASE_URL（默认 http://localhost:8080/api）
#   USERNAME / PASSWORD（登录账号）
#   QUESTION（自定义提问）
# ============================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
USERNAME="${USERNAME:-jwtuser}"
PASSWORD="${PASSWORD:-jwt123456}"
FLAG_DIR="${HOME}/opsagent-data/order-service"
QUESTION="${QUESTION:-服务器为什么变慢了？帮我找出根本原因并给出处置建议。}"

say() { printf '\n\033[1;36m========== %s ==========\033[0m\n' "$1"; }

say "① 注入故障：order-service 进入「数据库连接超时 + CPU 飙高」状态"
mkdir -p "${FLAG_DIR}"
touch "${FLAG_DIR}/fault"
sleep 8
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'

say "② 登录平台获取 JWT 令牌"
TOKEN=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
echo "登录成功，令牌长度 ${#TOKEN}"

say "③ 让 Agent 自主诊断"
echo "提问：${QUESTION}"
BODY=$(python3 -c 'import json,sys;print(json.dumps({"message":sys.argv[1],"sessionId":None}))' "${QUESTION}")
RESPONSE=$(curl -s -X POST "${BASE_URL}/ai/chat" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "${BODY}")

printf '%s' "${RESPONSE}" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
if payload.get("code") != 200:
    print("调用失败：", payload.get("message"))
    sys.exit(1)
data = payload["data"]
print("\n--- Agent 工具调用轨迹（共 %d 次）---" % len(data.get("toolCalls", [])))
for call in data.get("toolCalls", []):
    flag = "OK  " if call["success"] else "FAIL"
    print("  [%s] %-24s %-40s %6dms -> %s" % (
        flag, call["tool"], call["arguments"], call["durationMs"], call["resultSummary"]))
print("\n--- 诊断结论 ---")
print(data["answer"])
'

say "④ 恢复故障"
rm -f "${FLAG_DIR}/fault"
sleep 5
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'

say "⑤ 操作审计回放（Agent 提出的高危操作与人工确认记录）"
curl -s "${BASE_URL}/operations/audits?limit=5" \
  -H "Authorization: Bearer ${TOKEN}" | python3 -c '
import json, sys
rows = json.load(sys.stdin).get("data", [])
if not rows:
    print("  （暂无操作记录）")
for row in rows:
    print("  #%s %-20s target=%-16s risk=%-6s approve=%-9s exec=%-12s %sms" % (
        row["id"], row["operation"], row.get("target"), row["riskLevel"],
        row["approveStatus"], row["executionStatus"], row["executionTime"]))
'

say "演示结束"
echo "打开 http://虚拟机IP 进入控制台："
echo "  · AI 助手页可看到本次对话的完整工具调用轨迹；"
echo "  · 审计与观测页可看到成功率、耗时统计与操作审计记录。"
