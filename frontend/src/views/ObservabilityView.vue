<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getObservabilitySummary } from '../api/observability'
import { listAudits, listPendingOperations } from '../api/operation'

/**
 * 审计与可观测性页面。
 *
 * 展示两类内容：
 * 1. Agent 运行指标：任务数、工具调用次数、成功率、平均耗时、token 消耗；
 * 2. 操作审计：待确认操作 + 历史执行记录（谁提的、谁批的、结果如何）。
 *
 * 这些数据全部来自数据库里真实记录的表（agent_execution / command_audit / ai_usage），
 * 不是前端造出来的演示数据。
 */
const router = useRouter()

const loading = ref(false)
const summary = ref(null)
const audits = ref([])
const pending = ref([])
const chartRef = ref(null)
let chart = null

/** 工具名的中文说明，让界面更好读 */
const TOOL_LABELS = {
  list_servers: '查询服务器列表',
  get_server_overview: '查询实时指标',
  get_top_processes: '查询 Top 进程',
  get_container_list: '查询容器列表',
  get_container_logs: '读取容器日志',
  search_logs: '搜索日志',
  get_error_statistics: '日志错误统计',
  propose_restart_container: '提交重启操作',
  list_supported_operations: '查询操作白名单',
  list_pending_operations: '查询待确认操作'
}

function toolLabel(name) {
  return TOOL_LABELS[name] || name
}

const toolStats = computed(() => summary.value?.toolStats || [])

async function loadAll() {
  loading.value = true
  try {
    const [s, a, p] = await Promise.all([
      getObservabilitySummary(),
      listAudits(20),
      listPendingOperations()
    ])
    summary.value = s.data
    audits.value = a.data || []
    pending.value = p.data || []
    renderChart()
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chart) return
  // 横向条形图：数量最多的排最上面（ECharts 类目轴自下而上，所以要倒序）
  const data = [...toolStats.value].reverse()
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: 'rgba(13,21,36,0.94)',
        borderColor: 'rgba(34,211,238,0.35)',
        textStyle: { color: '#e2e8f0', fontSize: 12 }
      },
      grid: { left: 150, right: 60, top: 16, bottom: 16 },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#8fa3bb', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)', type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        data: data.map((t) => toolLabel(t.tool)),
        axisLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
        axisTick: { show: false },
        axisLabel: { color: '#9fb2c8', fontSize: 12 }
      },
      series: [
        {
          type: 'bar',
          data: data.map((t) => t.count),
          barWidth: 14,
          itemStyle: {
            borderRadius: [0, 7, 7, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#0891b2' },
              { offset: 1, color: '#22d3ee' }
            ])
          },
          label: {
            show: true,
            position: 'right',
            color: '#8fa3bb',
            fontSize: 11,
            formatter: (params) => {
              const item = data[params.dataIndex]
              return `${item.count} 次 · 均 ${item.avgDurationMs}ms${item.failCount > 0 ? ' · 失败 ' + item.failCount : ''}`
            }
          }
        }
      ]
    },
    true
  )
  chart.resize()
}

function riskTagType(risk) {
  if (risk === 'HIGH') return 'danger'
  if (risk === 'MEDIUM') return 'warning'
  return 'info'
}

function statusTagType(status) {
  if (status === 'SUCCESS' || status === 'APPROVED') return 'success'
  if (status === 'FAILED' || status === 'REJECTED') return 'danger'
  if (status === 'BLOCKED') return 'warning'
  return 'info'
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(5, 19)
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  await nextTick()
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
  await loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="console">
    <div class="bg-grid"></div>
    <div class="bg-orb"></div>

    <header class="topbar glass-panel">
      <div class="brand">
        <el-button link type="primary" @click="router.push({ name: 'servers' })">← 返回列表</el-button>
        <span class="divider"></span>
        <span class="brand-name">审计与可观测性</span>
        <span class="brand-sub">Agent 运行指标 · 操作全链路追踪</span>
      </div>
      <el-button type="primary" size="small" :loading="loading" @click="loadAll">
        <span class="refresh-icon" :class="{ spinning: loading }">⟳</span>
        刷新
      </el-button>
    </header>

    <main class="content">
      <!-- 指标卡片 -->
      <section class="stat-row">
        <div class="stat-card glass-panel fade-up">
          <span class="metric-label">今日任务</span>
          <div class="stat-value metric-value">
            {{ summary?.todayTasks ?? 0 }}<span class="metric-unit">次</span>
          </div>
        </div>
        <div class="stat-card glass-panel fade-up delay-1">
          <span class="metric-label">今日工具调用</span>
          <div class="stat-value metric-value">
            {{ summary?.todayToolCalls ?? 0 }}<span class="metric-unit">次</span>
          </div>
          <div class="stat-hint">累计 {{ summary?.totalToolCalls ?? 0 }} 次</div>
        </div>
        <div class="stat-card glass-panel fade-up delay-2">
          <span class="metric-label">工具调用成功率</span>
          <div class="stat-value metric-value ok">{{ summary?.successRate ?? 0 }}<span class="metric-unit">%</span></div>
        </div>
        <div class="stat-card glass-panel fade-up delay-3">
          <span class="metric-label">平均工具耗时</span>
          <div class="stat-value metric-value">
            {{ summary?.avgDurationMs ?? 0 }}<span class="metric-unit">ms</span>
          </div>
        </div>
        <div class="stat-card glass-panel fade-up delay-3">
          <span class="metric-label">今日 Tokens</span>
          <div class="stat-value metric-value">{{ (summary?.todayTokens ?? 0).toLocaleString() }}</div>
          <div class="stat-hint">累计 {{ (summary?.totalTokens ?? 0).toLocaleString() }}</div>
        </div>
        <div class="stat-card glass-panel fade-up delay-4">
          <span class="metric-label">待确认操作</span>
          <div class="stat-value metric-value" :class="{ warn: (summary?.operationPending ?? 0) > 0 }">
            {{ summary?.operationPending ?? 0 }}<span class="metric-unit">条</span>
          </div>
          <div class="stat-hint">
            已执行 {{ summary?.operationExecuted ?? 0 }} ｜ 失败 {{ summary?.operationFailed ?? 0 }} ｜ 被拒 {{ summary?.operationBlocked ?? 0 }}
          </div>
        </div>
      </section>

      <!-- 工具调用分布 -->
      <section class="panel glass-panel fade-up delay-2">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>工具调用分布</div>
          <div class="panel-meta">共 {{ toolStats.length }} 种工具被调用过</div>
        </div>
        <div v-if="toolStats.length === 0" class="empty-tip">还没有工具调用记录，先去 AI 助手问几个问题吧</div>
        <div v-show="toolStats.length > 0" ref="chartRef" class="chart"></div>
      </section>

      <!-- 待确认操作 -->
      <section v-if="pending.length" class="panel glass-panel fade-up delay-3">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>待人工确认的操作</div>
          <div class="panel-meta">高风险操作必须人工确认后才会执行</div>
        </div>
        <el-table :data="pending" stripe class="ops-table">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="operation" label="操作" width="170" />
          <el-table-column prop="target" label="目标" width="140" />
          <el-table-column prop="commandPreview" label="待执行命令" min-width="220" show-overflow-tooltip />
          <el-table-column label="风险" width="100">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small" effect="dark">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提出时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 审计记录 -->
      <section class="panel glass-panel fade-up delay-4">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>操作审计记录</div>
          <div class="panel-meta">最近 {{ audits.length }} 条 · 谁提的、谁批的、执行结果全程可追溯</div>
        </div>
        <el-table :data="audits" stripe class="ops-table">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="operation" label="操作" width="160" />
          <el-table-column prop="target" label="目标" width="130" />
          <el-table-column label="风险" width="100">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small" effect="dark">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.approveStatus)" size="small">{{ row.approveStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.executionStatus)" size="small">{{ row.executionStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">{{ row.executionTime ? row.executionTime + ' ms' : '—' }}</template>
          </el-table-column>
          <el-table-column prop="approvedBy" label="确认人" width="90">
            <template #default="{ row }">{{ row.approvedBy ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="执行时间" width="150">
            <template #default="{ row }">{{ formatTime(row.executedAt) }}</template>
          </el-table-column>
          <el-table-column prop="result" label="执行结果" min-width="200" show-overflow-tooltip />
        </el-table>
      </section>
    </main>
  </div>
</template>

<style scoped>
.console {
  position: relative;
  min-height: 100vh;
  padding: 18px 22px 30px;
  overflow: hidden;
  background: radial-gradient(circle at 15% 0%, #0f2233 0%, #070c15 45%, #04070d 100%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(34, 211, 238, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  background-size: 42px 42px;
  pointer-events: none;
}

.bg-orb {
  position: absolute;
  width: 460px;
  height: 460px;
  right: -160px;
  top: -180px;
  border-radius: 50%;
  background: #6366f1;
  opacity: 0.16;
  filter: blur(100px);
  pointer-events: none;
}

.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  margin-bottom: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.divider {
  width: 1px;
  height: 18px;
  background: rgba(148, 163, 184, 0.22);
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.6px;
  background: linear-gradient(90deg, #e2e8f0, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-sub {
  font-size: 12.5px;
  color: #7d90a8;
}

.refresh-icon {
  display: inline-block;
  margin-right: 4px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

.content {
  position: relative;
  z-index: 2;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  padding: 18px 20px 16px;
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.35s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 22px 46px rgba(2, 6, 23, 0.6), 0 0 0 1px rgba(34, 211, 238, 0.22);
}

.stat-value {
  margin-top: 8px;
  font-size: 30px;
  line-height: 1.1;
  color: #e2e8f0;
}

.stat-value.ok {
  color: #34d399;
}

.stat-value.warn {
  color: #fbbf24;
}

.stat-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #7d90a8;
}

.panel {
  padding: 18px 20px;
  margin-bottom: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.title-mark {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #22d3ee, #6366f1);
}

.panel-meta {
  font-size: 12.5px;
  color: #7d90a8;
}

.chart {
  width: 100%;
  height: 320px;
}

.empty-tip {
  padding: 50px 0;
  text-align: center;
  font-size: 13px;
  color: #7d90a8;
}

@media (max-width: 900px) {
  .console {
    padding: 12px 12px 24px;
  }
  .brand-sub {
    display: none;
  }
}
</style>
