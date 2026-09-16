<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chat, getUsage } from '../api/ai'
import { useUserStore } from '../stores/user'

/**
 * AI 助手对话页（M3.1：普通对话）。
 *
 * 当前版本只有一问一答；M3.2 接入工具调用后，
 * 这里会显示 Agent 调用了哪些工具、拿到了什么数据（工具执行轨迹）。
 */
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const sending = ref(false)
const listRef = ref(null)
const usage = ref(null)
const usageLoading = ref(false)

/** 余额展示文案：带币种符号；查不到时显示破折号 */
const balanceText = computed(() => {
  if (!usage.value || usage.value.balance == null) return '—'
  const symbol = usage.value.currency === 'USD' ? '$ ' : '¥ '
  return symbol + usage.value.balance
})

async function loadUsage() {
  usageLoading.value = true
  try {
    const res = await getUsage()
    usage.value = res.data
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    usageLoading.value = false
  }
}

const messages = ref([
  {
    role: 'assistant',
    content:
      '你好，我是 OpsAgent。\n\n' +
      '现在我可以回答 Linux、Docker、JVM 相关的运维问题。\n' +
      '下一步我会接入工具调用能力——那时你问"服务器 CPU 为什么高"，我会自己去采集数据再回答。'
  }
])

const samples = [
  '服务器 CPU 占用很高，应该怎么排查？',
  'Redis 容器为什么会被 OOM 杀掉？',
  'Java 服务频繁 Full GC 可能是什么原因？'
]

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const res = await chat(content)
    messages.value.push({ role: 'assistant', content: res.data.answer })
    // 每次对话后刷新用量（后端刚写入一条 ai_usage 记录）
    loadUsage()
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

onMounted(loadUsage)
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
        <span class="brand-sub">M3.1 · 普通对话</span>
      </div>
      <div class="topbar-right">
        <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
        <span class="user-role">{{ userStore.user?.role }}</span>
      </div>
    </header>

    <main class="content">
      <!-- 用量与余额概览 -->
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

      <!-- 余额异常提醒 -->
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

      <section class="chat-panel glass-panel fade-up">
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
            <div class="bubble">{{ msg.content }}</div>
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
            v-for="item in samples"
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

.content {
  position: relative;
  z-index: 2;
}

/* ---------- 用量与余额 ---------- */
.usage-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 34px;
  padding: 14px 20px;
  margin-bottom: 14px;
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
  margin-bottom: 14px;
  border-radius: 10px;
}

.refresh-icon {
  display: inline-block;
  margin-right: 4px;
}

.refresh-icon.spinning {
  animation: ops-spin 1s linear infinite;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  /* 页面顶部还有顶栏和用量条，这里要相应扣除，避免出现双滚动条 */
  height: calc(100vh - 300px);
  min-height: 320px;
  padding: 18px 20px;
}

@media (max-width: 900px) {
  .usage-strip {
    gap: 20px;
  }
  .usage-right {
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
  }
  .chat-panel {
    height: calc(100vh - 380px);
  }
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
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
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
  padding: 12px 2px 10px;
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
</style>
