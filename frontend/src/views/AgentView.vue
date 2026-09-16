<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  chat,
  deleteSession,
  getSessionMessages,
  getUsage,
  listSessions
} from '../api/ai'
import { useUserStore } from '../stores/user'

/**
 * AI 助手对话页（M3.3：多轮对话记忆 + 会话历史）。
 *
 * 左侧是会话列表（可以新建、切换、删除），右侧是对话区。
 * 每条助手消息上方会展示"Agent 执行过程"，也就是这次回答调用了哪些工具。
 */
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const sending = ref(false)
const listRef = ref(null)

const usage = ref(null)
const usageLoading = ref(false)

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([welcomeMessage()])

function welcomeMessage() {
  return {
    role: 'assistant',
    content:
      '你好，我是 OpsAgent。\n\n' +
      '我可以帮你查看服务器的实时状态（CPU、内存、磁盘、负载、进程），' +
      '也可以回答 Linux、Docker、JVM 的运维问题。\n\n' +
      '试着问我："这台服务器现在正常吗？"'
  }
}

/** 余额展示文案 */
const balanceText = computed(() => {
  if (!usage.value || usage.value.balance == null) return '—'
  const symbol = usage.value.currency === 'USD' ? '$ ' : '¥ '
  return symbol + usage.value.balance
})

/** 后端存的工具轨迹是 JSON 字符串，这里解析成数组 */
function parseToolCalls(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch (e) {
    return []
  }
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(5, 16)
}

async function loadUsage() {
  usageLoading.value = true
  try {
    const res = await getUsage()
    usage.value = res.data
  } catch (e) {
    // 提示由拦截器统一处理
  } finally {
    usageLoading.value = false
  }
}

async function loadSessions() {
  try {
    const res = await listSessions()
    sessions.value = res.data || []
  } catch (e) {
    // 提示由拦截器统一处理
  }
}

/** 打开一个历史会话：拉取消息并渲染 */
async function openSession(sessionId) {
  currentSessionId.value = sessionId
  try {
    const res = await getSessionMessages(sessionId)
    const loaded = (res.data || []).map((item) => ({
      role: item.role === 'USER' ? 'user' : 'assistant',
      content: item.content,
      toolCalls: parseToolCalls(item.toolCalls)
    }))
    messages.value = loaded.length ? loaded : [welcomeMessage()]
    scrollToBottom()
  } catch (e) {
    // 提示由拦截器统一处理
  }
}

/** 新建会话：只是把当前上下文清空，真正的会话记录在第一次提问时由后端创建 */
function newSession() {
  currentSessionId.value = null
  messages.value = [welcomeMessage()]
  input.value = ''
}

async function removeSession(sessionId) {
  try {
    await ElMessageBox.confirm('确定删除这个会话吗？删除后不可恢复。', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return // 用户取消
  }

  try {
    await deleteSession(sessionId)
    ElMessage.success('会话已删除')
    if (currentSessionId.value === sessionId) {
      newSession()
    }
    loadSessions()
  } catch (e) {
    // 提示由拦截器统一处理
  }
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const res = await chat(content, currentSessionId.value)
    // 新会话时后端会返回 sessionId，保存下来，后续提问就带上它实现"记忆"
    currentSessionId.value = res.data.sessionId
    messages.value.push({
      role: 'assistant',
      content: res.data.answer,
      toolCalls: res.data.toolCalls || []
    })
    loadUsage()
    loadSessions()
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '调用失败：' + (e.message || '未知错误'),
      error: true
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  loadUsage()
  loadSessions()
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
        <span class="brand-name">OpsAgent 助手</span>
        <span class="brand-sub">M3.3 · 多轮对话</span>
      </div>
      <div class="topbar-right">
        <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
        <span class="user-role">{{ userStore.user?.role }}</span>
      </div>
    </header>

    <main class="content">
      <!-- 用量与余额 -->
      <section class="usage-strip glass-panel fade-up">
        <div class="usage-item">
          <span class="metric-label">账户余额</span>
          <span class="usage-value" :class="{ danger: usage?.lowBalance }">{{ balanceText }}</span>
        </div>
        <div class="usage-item">
          <span class="metric-label">今日调用</span>
          <span class="usage-value">{{ (usage?.todayRequests ?? 0).toLocaleString() }} <i>次</i></span>
        </div>
        <div class="usage-item">
          <span class="metric-label">今日 Tokens</span>
          <span class="usage-value">{{ (usage?.todayTokens ?? 0).toLocaleString() }}</span>
        </div>
        <div class="usage-item">
          <span class="metric-label">累计 Tokens</span>
          <span class="usage-value">{{ (usage?.totalTokens ?? 0).toLocaleString() }}</span>
        </div>
        <div class="usage-right">
          <span class="usage-time">{{ usage?.checkedAt ? '余额更新于 ' + usage.checkedAt : '' }}</span>
          <el-button size="small" :loading="usageLoading" @click="loadUsage">
            <span class="refresh-icon" :class="{ spinning: usageLoading }">⟳</span>
            刷新
          </el-button>
        </div>
      </section>

      <el-alert
        v-if="usage?.balanceError"
        class="usage-alert"
        type="error"
        :closable="false"
        show-icon
        :title="`余额查询失败：${usage.balanceError}`"
      />
      <el-alert
        v-else-if="usage?.lowBalance"
        class="usage-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`账户余额仅剩 ${balanceText}，已低于告警阈值 ¥${usage.threshold}，请及时充值，否则 Agent 将无法继续回答`"
      />

      <div class="chat-layout">
        <!-- 会话列表 -->
        <aside class="session-panel glass-panel fade-up">
          <el-button class="new-btn" type="primary" plain @click="newSession">
            + 新建会话
          </el-button>

          <div class="session-list">
            <div
              v-for="item in sessions"
              :key="item.id"
              class="session-item"
              :class="{ active: item.id === currentSessionId }"
              @click="openSession(item.id)"
            >
              <div class="session-main">
                <div class="session-title">{{ item.title }}</div>
                <div class="session-time">{{ formatTime(item.updatedAt) }}</div>
              </div>
              <el-button
                class="session-del"
                link
                type="danger"
                size="small"
                @click.stop="removeSession(item.id)"
              >
                删除
              </el-button>
            </div>

            <div v-if="sessions.length === 0" class="session-empty">
              还没有历史会话<br />提问后会自动创建
            </div>
          </div>
        </aside>

        <!-- 对话区 -->
        <section class="chat-panel glass-panel fade-up delay-1">
          <div ref="listRef" class="message-list">
            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="message"
              :class="[msg.role, { error: msg.error }]"
            >
              <div class="avatar" :class="msg.role">
                {{ msg.role === 'user' ? '我' : 'AI' }}
              </div>
              <div class="bubble">
                <div v-if="msg.toolCalls?.length" class="tool-trace">
                  <div class="trace-title">
                    Agent 执行过程 · {{ msg.toolCalls.length }} 次工具调用
                  </div>
                  <div v-for="(call, i) in msg.toolCalls" :key="i" class="trace-item">
                    <span class="trace-icon" :class="{ fail: !call.success }">
                      {{ call.success ? '✓' : '✗' }}
                    </span>
                    <span class="trace-name">{{ call.tool }}</span>
                    <span class="trace-arg">{{ call.arguments }}</span>
                    <span class="trace-time">{{ call.durationMs }} ms</span>
                    <div class="trace-result">{{ call.resultSummary }}</div>
                  </div>
                </div>
                <span class="bubble-text">{{ msg.content }}</span>
              </div>
            </div>

            <div v-if="sending" class="message assistant">
              <div class="avatar assistant">AI</div>
              <div class="bubble typing">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>

          <div class="samples">
            <span class="samples-label">试试这样问：</span>
            <el-tag
              v-for="item in ['这台服务器现在正常吗？', 'CPU 占用最高的进程是哪些？', 'Redis 为什么会 OOM？']"
              :key="item"
              class="sample-tag"
              size="small"
              effect="plain"
              @click="send(item)"
            >
              {{ item }}
            </el-tag>
          </div>

          <div class="input-area">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="描述你遇到的运维问题，Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="send()"
            />
            <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.console {
  position: relative;
  min-height: 100vh;
  padding: 18px 22px 26px;
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

/* ---------- 顶栏 ---------- */
.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  margin-bottom: 14px;
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

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.user-name {
  color: #dbe6f3;
}

.user-role {
  color: var(--ops-primary);
  font-size: 12px;
}

/* ---------- 用量条 ---------- */
.content {
  position: relative;
  z-index: 2;
}

.usage-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 34px;
  padding: 14px 20px;
  margin-bottom: 12px;
}

.usage-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.usage-value {
  font-size: 20px;
  font-weight: 700;
  color: #e2e8f0;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
}

.usage-value i {
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
  color: #7d90a8;
}

.usage-value.danger {
  color: #f87171;
}

.usage-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.usage-time {
  font-size: 12px;
  color: #7d90a8;
}

.usage-alert {
  margin-bottom: 12px;
  border-radius: 10px;
}

.refresh-icon {
  display: inline-block;
  margin-right: 4px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

/* ---------- 会话 + 对话 两栏布局 ---------- */
.chat-layout {
  display: flex;
  gap: 14px;
  height: calc(100vh - 290px);
  min-height: 360px;
}

.session-panel {
  flex: 0 0 250px;
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  overflow: hidden;
}

.new-btn {
  width: 100%;
  margin-bottom: 12px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  margin-bottom: 6px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.25s ease;
}

.session-item:hover {
  background: rgba(148, 163, 184, 0.08);
  border-color: rgba(148, 163, 184, 0.18);
}

.session-item.active {
  background: rgba(34, 211, 238, 0.1);
  border-color: rgba(34, 211, 238, 0.4);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  color: #dbe6f3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  margin-top: 2px;
  font-size: 11px;
  color: #6b7d93;
}

.session-del {
  opacity: 0;
  transition: opacity 0.2s ease;
}

.session-item:hover .session-del {
  opacity: 1;
}

.session-empty {
  padding: 30px 8px;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.9;
  color: #6b7d93;
}

/* ---------- 对话区 ---------- */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 18px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 6px;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
  animation: ops-fade-up 0.35s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.avatar.assistant {
  color: #06121b;
  background: linear-gradient(135deg, #22d3ee, #6366f1);
}

.avatar.user {
  color: #cbd5e1;
  background: rgba(148, 163, 184, 0.16);
  border: 1px solid rgba(148, 163, 184, 0.24);
}

.bubble {
  max-width: 76%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.bubble-text {
  white-space: pre-wrap;
}

.message.assistant .bubble {
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.14);
  color: #dbe6f3;
}

.message.user .bubble {
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.18), rgba(99, 102, 241, 0.18));
  border: 1px solid rgba(34, 211, 238, 0.35);
  color: #eaf6ff;
}

.message.error .bubble {
  border-color: rgba(248, 113, 113, 0.45);
  background: rgba(248, 113, 113, 0.1);
  color: #fecaca;
}

/* Agent 工具调用轨迹 */
.tool-trace {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(34, 211, 238, 0.06);
  border: 1px solid rgba(34, 211, 238, 0.2);
}

.trace-title {
  margin-bottom: 8px;
  font-size: 11.5px;
  letter-spacing: 0.6px;
  color: #7dd3fc;
}

.trace-item {
  margin-bottom: 8px;
  font-size: 12px;
}

.trace-item:last-child {
  margin-bottom: 0;
}

.trace-icon {
  margin-right: 6px;
  color: #34d399;
  font-weight: 700;
}

.trace-icon.fail {
  color: #f87171;
}

.trace-name {
  color: #e2e8f0;
  font-weight: 600;
}

.trace-arg {
  margin-left: 6px;
  color: #7d90a8;
}

.trace-time {
  float: right;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.trace-result {
  margin-top: 3px;
  padding-left: 16px;
  line-height: 1.5;
  color: #93a7bd;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 打字中动画 */
.typing span {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 4px;
  border-radius: 50%;
  background: #22d3ee;
  animation: ops-float 1s ease-in-out infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.samples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 2px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.samples-label {
  font-size: 12.5px;
  color: #7d90a8;
}

.sample-tag {
  cursor: pointer;
  transition: all 0.25s ease;
}

.sample-tag:hover {
  transform: translateY(-2px);
  border-color: rgba(34, 211, 238, 0.6);
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-area .el-textarea {
  flex: 1;
}

.input-area :deep(.el-textarea__inner) {
  background: rgba(9, 15, 26, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #e2e8f0;
  font-size: 14px;
  line-height: 1.6;
}

.input-area :deep(.el-textarea__inner:focus) {
  border-color: rgba(34, 211, 238, 0.6);
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.25), 0 0 18px rgba(34, 211, 238, 0.15);
}

/* ---------- 小屏自适应 ---------- */
@media (max-width: 1000px) {
  .chat-layout {
    flex-direction: column;
    height: auto;
  }
  .session-panel {
    flex: 0 0 auto;
    max-height: 180px;
  }
  .chat-panel {
    height: 60vh;
  }
  .usage-right {
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
  }
}

/* 样式版本标记：v2（会话列表 + 多轮对话） */
</style>
