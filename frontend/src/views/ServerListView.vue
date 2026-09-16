<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listServers } from '../api/server'
import { useUserStore } from '../stores/user'
import { useCountUp } from '../composables/useCountUp'

/**
 * 服务器列表（控制台首页）。
 *
 * 数据链路：onMounted → /api/servers → axios 自动带令牌 → 后端验签 → 返回列表
 * 界面亮点：统计卡片数字滚动、状态点呼吸、刷新按钮旋转、首屏骨架屏。
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const firstLoadDone = ref(false)
const servers = ref([])
const lastRefresh = ref('')

const totalCount = computed(() => servers.value.length)
const onlineCount = computed(() => servers.value.filter((s) => s.status === 'ONLINE').length)
const offlineCount = computed(() => totalCount.value - onlineCount.value)

// 数字滚动动画
const totalAnim = useCountUp(totalCount)
const onlineAnim = useCountUp(onlineCount)
const offlineAnim = useCountUp(offlineCount)

async function loadServers() {
  loading.value = true
  try {
    const res = await listServers()
    servers.value = res.data || []
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN')
  } catch (e) {
    // 401 已由 axios 拦截器统一处理（清登录态 + 跳登录页）
  } finally {
    loading.value = false
    firstLoadDone.value = true
  }
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

/** 跳转到该服务器的监控详情页 */
function goMonitor(row) {
  router.push({ name: 'monitor', params: { id: row.id } })
}

onMounted(loadServers)
</script>

<template>
  <div class="console">
    <!-- 背景装饰 -->
    <div class="bg-grid"></div>
    <div class="bg-orb"></div>

    <header class="topbar glass-panel">
      <div class="brand">
        <div class="brand-dot"></div>
        <span class="brand-name">OpsAgent</span>
        <span class="brand-sub">智能运维控制台</span>
      </div>
      <div class="topbar-right">
        <el-button size="small" type="primary" plain @click="router.push({ name: 'agent' })">
          AI 助手
        </el-button>
        <div class="user-chip">
          <div class="avatar">{{ (userStore.user?.nickname || userStore.user?.username || 'U').slice(0, 1) }}</div>
          <div class="user-meta">
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
            <span class="user-role">{{ userStore.user?.role }}</span>
          </div>
        </div>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <!-- 统计卡片 -->
      <section class="stats fade-up">
        <div class="stat-card glass-panel">
          <div class="stat-label">纳管服务器</div>
          <div class="stat-value">{{ totalAnim }}</div>
          <div class="stat-bar"><i style="width: 100%"></i></div>
        </div>

        <div class="stat-card glass-panel delay-1 fade-up">
          <div class="stat-label">在线</div>
          <div class="stat-value online">{{ onlineAnim }}</div>
          <div class="stat-bar"><i class="bar-online" :style="{ width: totalCount ? (onlineCount / totalCount) * 100 + '%' : '0%' }"></i></div>
        </div>

        <div class="stat-card glass-panel delay-2 fade-up">
          <div class="stat-label">离线</div>
          <div class="stat-value offline">{{ offlineAnim }}</div>
          <div class="stat-bar"><i class="bar-offline" :style="{ width: totalCount ? (offlineCount / totalCount) * 100 + '%' : '0%' }"></i></div>
        </div>

        <div class="stat-card glass-panel delay-3 fade-up">
          <div class="stat-label">平台状态</div>
          <div class="stat-value ok">正常</div>
          <div class="stat-hint">上次刷新：{{ lastRefresh || '—' }}</div>
        </div>
      </section>

      <!-- 服务器表格 -->
      <section class="table-card glass-panel fade-up delay-2">
        <div class="card-head">
          <div class="card-title">
            <span class="title-mark"></span>
            服务器列表
          </div>
          <el-button type="primary" size="small" :loading="loading" @click="loadServers">
            <span class="refresh-icon" :class="{ spinning: loading }">⟳</span>
            刷新
          </el-button>
        </div>

        <el-skeleton v-if="!firstLoadDone" :rows="4" animated style="padding: 12px" />

        <el-table v-else :data="servers" stripe class="ops-table">
          <el-table-column prop="id" label="ID" width="72" align="center" />
          <el-table-column prop="name" label="服务器名称" min-width="150" />
          <el-table-column prop="host" label="主机地址" min-width="150" />
          <el-table-column prop="port" label="SSH 端口" width="100" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <span class="status">
                <i class="dot" :class="row.status === 'ONLINE' ? 'dot-online' : 'dot-offline'"></i>
                {{ row.status }}
              </span>
            </template>
          </el-table-column>
          <!-- show-overflow-tooltip：内容过长时省略并悬浮显示，避免把单元格挤成竖排文字 -->
          <el-table-column prop="os" label="操作系统" min-width="170" show-overflow-tooltip />
          <el-table-column prop="description" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="112" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" plain size="small" @click="goMonitor(row)">监控</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-foot">
          共 <span class="metric-value">{{ servers.length }}</span> 台服务器
        </div>
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
  opacity: 0.22;
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
  gap: 10px;
}

.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--ops-primary);
  animation: ops-pulse 2.6s ease-out infinite;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
  background: linear-gradient(90deg, #e2e8f0, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-sub {
  font-size: 12px;
  color: var(--ops-text-dim);
  padding-left: 10px;
  border-left: 1px solid rgba(148, 163, 184, 0.25);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 12px 5px 5px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.16);
  transition: all 0.3s ease;
}

.user-chip:hover {
  border-color: rgba(34, 211, 238, 0.45);
  box-shadow: 0 6px 18px rgba(34, 211, 238, 0.16);
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #06121b;
  font-weight: 700;
  background: linear-gradient(135deg, #22d3ee, #6366f1);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
}

.user-role {
  font-size: 11px;
  color: var(--ops-primary);
}

/* ---------- 内容区 ---------- */
.content {
  position: relative;
  z-index: 2;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  position: relative;
  padding: 18px 20px;
  overflow: hidden;
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.35s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 22px 46px rgba(2, 6, 23, 0.6), 0 0 0 1px rgba(34, 211, 238, 0.25);
}

.stat-label {
  font-size: 13px;
  color: var(--ops-text-dim);
}

.stat-value {
  margin-top: 6px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #e2e8f0;
}

.stat-value.online {
  color: #34d399;
}

.stat-value.offline {
  color: #f87171;
}

.stat-value.ok {
  font-size: 26px;
  color: var(--ops-primary);
}

.stat-hint {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(148, 163, 184, 0.75);
}

.stat-bar {
  margin-top: 14px;
  height: 4px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.stat-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0891b2, #22d3ee);
  transition: width 0.9s cubic-bezier(0.22, 1, 0.36, 1);
}

.stat-bar i.bar-online {
  background: linear-gradient(90deg, #059669, #34d399);
}

.stat-bar i.bar-offline {
  background: linear-gradient(90deg, #b91c1c, #f87171);
}

/* ---------- 表格卡片 ---------- */
.table-card {
  padding: 18px 20px 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}

.title-mark {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #22d3ee, #6366f1);
}

.refresh-icon {
  display: inline-block;
  margin-right: 4px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

.ops-table {
  background: transparent;
}

.ops-table :deep(.el-table__header th) {
  background: rgba(148, 163, 184, 0.06) !important;
  font-weight: 600;
}

.table-foot {
  padding: 14px 2px 10px;
  font-size: 12.5px;
  color: #7d90a8;
  text-align: right;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-online {
  background: #34d399;
  animation: ops-pulse 2.2s ease-out infinite;
}

.dot-offline {
  background: #f87171;
}
</style>
