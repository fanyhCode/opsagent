<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getHistory, getOverview } from '../api/monitor'

/**
 * 服务器监控页。
 *
 * 数据来源：
 * - 实时指标 /overview：点刷新时现场 SSH 采集一次，含进程 Top5；
 * - 历史趋势 /history：后端定时任务每 30 秒写一条，页面取最近 40 条画曲线。
 *
 * 刷新机制：右上角开关控制"自动实时刷新"，默认每 15 秒一次并显示倒计时；
 * 也可以随时手动点"刷新"立刻采集一次。
 */
const route = useRoute()
const router = useRouter()
const serverId = Number(route.params.id)

/** 自动刷新间隔（秒） */
const REFRESH_SECONDS = 15

const loading = ref(false)
const overview = ref(null)
const history = ref([])
const autoRefresh = ref(true)
const countdown = ref(REFRESH_SECONDS)
const lastRefreshTime = ref('')

const chartRef = ref(null)
let chart = null
let tickTimer = null
let resizeObserver = null

const processes = computed(() => overview.value?.topProcesses || [])
const cpu = computed(() => overview.value?.cpuUsagePercent ?? 0)
const memPercent = computed(() => overview.value?.memory?.usagePercent ?? 0)
const diskPercent = computed(() => parseFloat(overview.value?.disk?.usePercent) || 0)
const load1 = computed(() => overview.value?.load1 ?? 0)

/** 数值配色：正常青色、偏高橙色、危险红色 */
function levelColor(value) {
  if (value >= 90) return '#f87171'
  if (value >= 75) return '#fbbf24'
  return '#22d3ee'
}

async function loadAll(showTip = false) {
  if (loading.value) return
  loading.value = true
  try {
    const [ov, his] = await Promise.all([getOverview(serverId), getHistory(serverId, 40)])
    overview.value = ov.data
    // 后端按时间倒序返回，画图需要正序
    history.value = (his.data || []).slice().reverse()
    lastRefreshTime.value = new Date().toLocaleTimeString('zh-CN')
    renderChart()
    countdown.value = REFRESH_SECONDS
    if (showTip) ElMessage.success('已刷新')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function renderChart() {
  ensureChart()
  if (!chart) return

  const times = history.value.map((item) => (item.createdAt || '').slice(11, 19))
  const cpuData = history.value.map((item) => item.cpuUsage)
  const memData = history.value.map((item) => item.memoryUsage)
  const diskData = history.value.map((item) => item.diskUsage)
  // 数据点少的时候把标记点显示出来，否则一条孤零零的线看不出采样时刻
  const showSymbol = history.value.length <= 24

  chart.setOption(
    {
      backgroundColor: 'transparent',
      animationDuration: 500,
      animationEasing: 'cubicOut',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(13, 21, 36, 0.94)',
        borderColor: 'rgba(34, 211, 238, 0.35)',
        borderWidth: 1,
        padding: [10, 14],
        textStyle: { color: '#e2e8f0', fontSize: 12 },
        valueFormatter: (value) => `${value}%`,
        axisPointer: { type: 'line', lineStyle: { color: 'rgba(34,211,238,0.45)', width: 1 } }
      },
      legend: {
        // 图例固定在右上角，避免和 Y 轴刻度重叠
        data: ['CPU', '内存', '磁盘'],
        right: 6,
        top: 4,
        itemWidth: 14,
        itemHeight: 8,
        itemGap: 18,
        icon: 'roundRect',
        textStyle: { color: '#9fb2c8', fontSize: 12 }
      },
      grid: { left: 58, right: 26, top: 60, bottom: 38 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: times,
        axisLine: { lineStyle: { color: 'rgba(148,163,184,0.22)' } },
        axisTick: { show: false },
        axisLabel: { color: '#8fa3bb', fontSize: 11, hideOverlap: true, margin: 12 }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        splitNumber: 5,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#8fa3bb', fontSize: 11, formatter: '{value}%' },
        splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)', type: 'dashed' } }
      },
      series: [
        {
          name: 'CPU',
          type: 'line',
          smooth: 0.35,
          showSymbol,
          symbolSize: 6,
          data: cpuData,
          lineStyle: { width: 2.4, color: '#22d3ee' },
          itemStyle: { color: '#22d3ee' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(34,211,238,0.32)' },
              { offset: 1, color: 'rgba(34,211,238,0.01)' }
            ])
          }
        },
        {
          name: '内存',
          type: 'line',
          smooth: 0.35,
          showSymbol,
          symbolSize: 6,
          data: memData,
          lineStyle: { width: 2.4, color: '#818cf8' },
          itemStyle: { color: '#818cf8' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(129,140,248,0.28)' },
              { offset: 1, color: 'rgba(129,140,248,0.01)' }
            ])
          }
        },
        {
          name: '磁盘',
          type: 'line',
          smooth: 0.35,
          showSymbol,
          symbolSize: 6,
          data: diskData,
          lineStyle: { width: 2.4, color: '#34d399' },
          itemStyle: { color: '#34d399' }
      }
    ]
  },
    true
  )

  // 每次更新数据后校正一次尺寸：
  // 图表容器的宽高可能因为窗口缩放、侧边栏展开等原因变化，不校正就会出现画布被拉伸的错位
  chart.resize()
}

/**
 * 懒初始化 ECharts 实例。
 *
 * 为什么要判断 clientWidth？如果容器还没完成布局（宽度为 0），
 * echarts.init 会按默认的小尺寸创建画布，之后再被 CSS 拉伸，图形就全错位了。
 * 这里等在布局完成后再初始化，从根上避免这个问题。
 */
function ensureChart() {
  if (chart) return
  const el = chartRef.value
  if (!el || el.clientWidth === 0) return
  chart = echarts.init(el)
}

function handleResize() {
  chart?.resize()
}

function goBack() {
  router.push({ name: 'servers' })
}

/** 每秒钟走一次倒计时，归零就自动刷新一次 */
function startCountdown() {
  tickTimer = setInterval(() => {
    if (!autoRefresh.value) return
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = REFRESH_SECONDS
      loadAll()
    }
  }, 1000)
}

// 关闭自动刷新时，把倒计时重置，避免恢复后立刻触发
watch(autoRefresh, (enabled) => {
  countdown.value = REFRESH_SECONDS
  if (enabled) ElMessage.success('已开启自动刷新')
})

onMounted(async () => {
  await nextTick()
  ensureChart()
  await loadAll()
  startCountdown()
  window.addEventListener('resize', handleResize)
  // 用 ResizeObserver 监听容器自身的尺寸变化（比只监听 window.resize 更可靠：
  // 侧边栏折叠、字体加载、父容器变化都能触发）
  if (window.ResizeObserver && chartRef.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartRef.value)
  }
})

onUnmounted(() => {
  clearInterval(tickTimer)
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  // ECharts 实例必须手动销毁，否则反复进出页面会造成内存泄漏
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
        <el-button link type="primary" class="back-btn" @click="goBack">← 返回列表</el-button>
        <span class="divider"></span>
        <span class="server-name">{{ overview?.hostname || '服务器' }}</span>
        <span class="server-meta">{{ overview?.uptime || '读取中…' }}</span>
      </div>

      <div class="topbar-right">
        <span class="live">
          <i class="live-dot" :class="{ paused: !autoRefresh }"></i>
          {{ autoRefresh ? `自动刷新中 · ${countdown}s` : '自动刷新已暂停' }}
        </span>
        <el-switch v-model="autoRefresh" size="small" inline-prompt active-text="自动" inactive-text="手动" />
        <el-button type="primary" :loading="loading" @click="loadAll(true)">
          <span class="refresh-icon" :class="{ spinning: loading }">⟳</span>
          刷新
        </el-button>
      </div>
    </header>

    <main class="content">
      <!-- 指标卡片 -->
      <section class="stat-row">
        <div class="stat-card glass-panel fade-up">
          <div class="stat-head">
            <span class="metric-label">CPU 使用率</span>
            <span class="stat-icon cpu">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="7" y="7" width="10" height="10" rx="2" />
                <path d="M4 10h3M4 14h3M17 10h3M17 14h3M10 4v3M14 4v3M10 17v3M14 17v3" />
              </svg>
            </span>
          </div>
          <div class="stat-value metric-value" :style="{ color: levelColor(cpu) }">
            {{ cpu }}<span class="metric-unit">%</span>
          </div>
          <div class="bar"><i :style="{ width: cpu + '%', background: levelColor(cpu) }"></i></div>
          <div class="stat-hint">1 分钟负载 <b>{{ load1 }}</b></div>
        </div>

        <div class="stat-card glass-panel fade-up delay-1">
          <div class="stat-head">
            <span class="metric-label">内存使用率</span>
            <span class="stat-icon mem">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="3" y="7" width="18" height="10" rx="2" />
                <path d="M7 17v3M12 17v3M17 17v3M8 11h8" />
              </svg>
            </span>
          </div>
          <div class="stat-value metric-value" :style="{ color: levelColor(memPercent) }">
            {{ memPercent }}<span class="metric-unit">%</span>
          </div>
          <div class="bar"><i :style="{ width: memPercent + '%', background: levelColor(memPercent) }"></i></div>
          <div class="stat-hint">
            <b>{{ overview?.memory?.usedMb ?? 0 }}</b> MB / 共 {{ overview?.memory?.totalMb ?? 0 }} MB
          </div>
        </div>

        <div class="stat-card glass-panel fade-up delay-2">
          <div class="stat-head">
            <span class="metric-label">磁盘使用率</span>
            <span class="stat-icon disk">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <ellipse cx="12" cy="6" rx="8" ry="3" />
                <path d="M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6" />
                <path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
              </svg>
            </span>
          </div>
          <div class="stat-value metric-value" :style="{ color: levelColor(diskPercent) }">
            {{ diskPercent }}<span class="metric-unit">%</span>
          </div>
          <div class="bar"><i :style="{ width: diskPercent + '%', background: levelColor(diskPercent) }"></i></div>
          <div class="stat-hint">
            {{ overview?.disk?.used || '—' }} / {{ overview?.disk?.size || '—' }}（{{ overview?.disk?.mountedOn || '/' }}）
          </div>
        </div>

        <div class="stat-card glass-panel fade-up delay-3">
          <div class="stat-head">
            <span class="metric-label">系统负载</span>
            <span class="stat-icon load">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M3 17l4.5-6 3.5 4 3-5 3 3 4-6" />
              </svg>
            </span>
          </div>
          <div class="stat-value metric-value ok">{{ load1 }}</div>
          <div class="bar"><i :style="{ width: Math.min(load1 * 20, 100) + '%' }"></i></div>
          <div class="stat-hint">5 分钟 <b>{{ overview?.load5 ?? 0 }}</b> ｜ 15 分钟 <b>{{ overview?.load15 ?? 0 }}</b></div>
        </div>
      </section>

      <!-- 趋势图 -->
      <section class="panel glass-panel fade-up delay-2">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>指标趋势</div>
          <div class="panel-meta">
            <span>最近 {{ history.length }} 个采样点</span>
            <span class="dot-sep">·</span>
            <span>上次刷新 {{ lastRefreshTime || '—' }}</span>
          </div>
        </div>
        <div class="chart-wrap">
          <!-- 注意：图表容器始终渲染，不能用 v-show 隐藏，
               否则 ECharts 初始化时拿到 0 宽度，画布会被拉伸变形 -->
          <div ref="chartRef" class="chart"></div>
          <div v-if="history.length === 0" class="empty-tip">
            暂无历史数据，后台采集任务运行后会自动出现曲线（首次约需 30 秒）
          </div>
        </div>
      </section>

      <!-- 进程 Top5 -->
      <section class="panel glass-panel fade-up delay-3">
        <div class="panel-head">
          <div class="panel-title"><span class="title-mark"></span>CPU 占用最高进程</div>
          <div class="panel-meta"><span>共 {{ processes.length }} 个进程</span></div>
        </div>
        <el-table :data="processes" stripe class="ops-table">
          <el-table-column prop="pid" label="PID" width="110" />
          <el-table-column prop="command" label="进程名" width="220" />
          <el-table-column label="CPU 占用">
            <template #default="{ row }">
              <span class="mini-bar">
                <i :style="{ width: Math.min(row.cpuPercent, 100) + '%', background: levelColor(row.cpuPercent) }"></i>
              </span>
              <span class="mini-text metric-value">{{ row.cpuPercent }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="内存占用" width="160">
            <template #default="{ row }">
              <span class="metric-value">{{ row.memPercent }}%</span>
            </template>
          </el-table-column>
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
  background: #0ea5e9;
  opacity: 0.18;
  filter: blur(100px);
  pointer-events: none;
}

/* ---------- 顶栏 ---------- */
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

.back-btn {
  font-size: 13px;
}

.divider {
  width: 1px;
  height: 18px;
  background: rgba(148, 163, 184, 0.22);
}

.server-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.6px;
  background: linear-gradient(90deg, #e2e8f0, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.server-meta {
  font-size: 12.5px;
  color: #7d90a8;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: #9fb2c8;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  animation: ops-pulse 2s ease-out infinite;
}

.live-dot.paused {
  background: #94a3b8;
  animation: none;
}

.refresh-icon {
  display: inline-block;
  margin-right: 5px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

/* ---------- 内容区 ---------- */
.content {
  position: relative;
  z-index: 2;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
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

.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(148, 163, 184, 0.06);
}

.stat-icon.cpu {
  color: #22d3ee;
  border-color: rgba(34, 211, 238, 0.3);
  background: rgba(34, 211, 238, 0.1);
}

.stat-icon.mem {
  color: #818cf8;
  border-color: rgba(129, 140, 248, 0.3);
  background: rgba(129, 140, 248, 0.1);
}

.stat-icon.disk {
  color: #34d399;
  border-color: rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.1);
}

.stat-icon.load {
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.3);
  background: rgba(251, 191, 36, 0.1);
}

.stat-value {
  margin-top: 10px;
  font-size: 36px;
  line-height: 1.1;
}

.stat-value.ok {
  color: var(--ops-primary);
}

.stat-hint {
  margin-top: 12px;
  font-size: 12.5px;
  color: #7d90a8;
}

.stat-hint b {
  color: #cbd5e1;
  font-weight: 600;
}

.bar {
  margin-top: 14px;
  height: 5px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.8s cubic-bezier(0.22, 1, 0.36, 1), background 0.4s ease;
}

/* ---------- 面板 ---------- */
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
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: #7d90a8;
}

.dot-sep {
  color: rgba(148, 163, 184, 0.4);
}

.chart-wrap {
  position: relative;
}

.chart {
  /* 高度随视口自适应，避免固定高度在大屏上显得空、小屏上被挤压 */
  height: clamp(260px, 42vh, 440px);
  width: 100%;
}

.empty-tip {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #7d90a8;
}

/* ---------- 小屏自适应 ---------- */
@media (max-width: 1100px) {
  .topbar {
    flex-wrap: wrap;
    gap: 12px;
  }
  .topbar-right {
    flex-wrap: wrap;
  }
  .live {
    order: 3;
    width: 100%;
  }
}

@media (max-width: 768px) {
  .console {
    padding: 12px 12px 24px;
  }
  .stat-value {
    font-size: 30px;
  }
  .panel-head {
    flex-wrap: wrap;
    gap: 8px;
  }
  .chart {
    height: 260px;
  }
}

.mini-bar {
  display: inline-block;
  width: 100px;
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
  vertical-align: middle;
}

.mini-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.mini-text {
  margin-left: 10px;
  font-size: 12.5px;
  color: #cbd5e1;
}

/* 样式版本标记：v2（用于确认浏览器加载到的是最新样式） */
</style>
