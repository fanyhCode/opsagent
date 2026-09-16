<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/auth'
import { useUserStore } from '../stores/user'
import ParticleBackground from '../components/ParticleBackground.vue'

/**
 * 登录 / 注册页面（左右两栏布局）。
 *
 * 左栏：品牌、标语、核心能力介绍、技术标签 —— 让页面"有内容"，也顺便讲清楚项目是什么。
 * 右栏：登录 / 注册卡片。
 * 所有动效都用原生 CSS + Canvas 实现，不额外引入动画库。
 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const shake = ref(false)

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '' })

/* ---------------- 打字机标语 ---------------- */
const slogans = [
  'AI 自主调用工具，多轮定位故障根因',
  '命令白名单 + 风险分级 + 人工确认',
  '指标 · 日志 · 容器 · 知识库 关联分析'
]
const typedText = ref('')
let typeTimer = null
let sloganIndex = 0
let charIndex = 0
let deleting = false

function tick() {
  const current = slogans[sloganIndex]
  if (!deleting) {
    charIndex++
    typedText.value = current.slice(0, charIndex)
    if (charIndex >= current.length) {
      deleting = true
      typeTimer = setTimeout(tick, 1800)
      return
    }
  } else {
    charIndex--
    typedText.value = current.slice(0, charIndex)
    if (charIndex <= 0) {
      deleting = false
      sloganIndex = (sloganIndex + 1) % slogans.length
    }
  }
  typeTimer = setTimeout(tick, deleting ? 35 : 85)
}

onMounted(() => {
  typeTimer = setTimeout(tick, 500)
})

onUnmounted(() => {
  clearTimeout(typeTimer)
})

/* ---------------- 交互逻辑 ---------------- */
async function handleLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    shakeCard()
    return
  }
  loading.value = true
  try {
    const res = await login(loginForm.value)
    userStore.setLogin(res.data.token, {
      id: res.data.id,
      username: res.data.username,
      nickname: res.data.nickname,
      role: res.data.role
    })
    ElMessage.success('登录成功，正在进入控制台')
    router.push(route.query.redirect || '/servers')
  } catch (e) {
    shakeCard()
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerForm.value.username || !registerForm.value.password) {
    ElMessage.warning('请填写用户名和密码')
    shakeCard()
    return
  }
  if (registerForm.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于 6 位')
    shakeCard()
    return
  }
  // 两次密码必须一致，避免用户手误打错又记不住
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    shakeCard()
    return
  }
  loading.value = true
  try {
    // 只提交用户名和密码：昵称由后端默认取用户名，不再让用户填
    await register({
      username: registerForm.value.username,
      password: registerForm.value.password
    })
    ElMessage.success('注册成功，请登录')
    loginForm.value.username = registerForm.value.username
    loginForm.value.password = ''
    activeTab.value = 'login'
  } catch (e) {
    shakeCard()
  } finally {
    loading.value = false
  }
}

function shakeCard() {
  shake.value = true
  setTimeout(() => (shake.value = false), 520)
}
</script>

<template>
  <div class="login-page">
    <!-- 背景三层：粒子网络 / 渐变光斑 / 网格 -->
    <ParticleBackground />
    <div class="orb orb-a"></div>
    <div class="orb orb-b"></div>
    <div class="grid-overlay"></div>

    <div class="hero">
      <!-- ============ 左栏：品牌与能力介绍 ============ -->
      <section class="hero-left fade-up">
        <div class="brand-row">
          <div class="brand-mark">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="3" y="4" width="18" height="6" rx="2" />
              <rect x="3" y="14" width="18" height="6" rx="2" />
              <circle cx="7" cy="7" r="1.2" fill="currentColor" stroke="none" />
              <circle cx="7" cy="17" r="1.2" fill="currentColor" stroke="none" />
            </svg>
          </div>
          <div class="brand-text">
            <span class="brand-name">OpsAgent</span>
            <span class="brand-sub">智能运维与故障诊断平台</span>
          </div>
          <span class="version-tag">M1</span>
        </div>

        <h1 class="hero-title">
          让 AI 成为你的<br />
          <span>Linux 运维专家</span>
        </h1>

        <p class="hero-slogan">
          <span class="typed">{{ typedText }}</span><span class="caret">|</span>
        </p>

        <ul class="feature-list">
          <li class="feature">
            <span class="ic">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M12 3v3M12 18v3M3 12h3M18 12h3" />
                <circle cx="12" cy="12" r="4" />
              </svg>
            </span>
            <div>
              <div class="ft">多轮工具调用</div>
              <div class="fd">自动查询 CPU、进程、日志、容器与 JVM 状态</div>
            </div>
          </li>
          <li class="feature">
            <span class="ic">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z" />
                <path d="M9.5 12.5l1.8 1.8 3.4-3.6" />
              </svg>
            </span>
            <div>
              <div class="ft">命令安全执行</div>
              <div class="fd">白名单 + 参数校验 + 风险分级 + 人工确认</div>
            </div>
          </li>
          <li class="feature">
            <span class="ic">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5z" />
                <path d="M8 7.5h7M8 11h5" />
              </svg>
            </span>
            <div>
              <div class="ft">故障知识库</div>
              <div class="fd">RAG 检索历史案例，结合实时监控辅助定位</div>
            </div>
          </li>
        </ul>

        <div class="tech-tags">
          <span>Spring Boot 3</span>
          <span>Spring AI</span>
          <span>JWT</span>
          <span>MyBatis-Plus</span>
          <span>Vue 3</span>
          <span>Docker</span>
        </div>
      </section>

      <!-- ============ 右栏：登录卡片 ============ -->
      <section class="login-card glass-panel fade-up delay-2" :class="{ shake }">
        <div class="card-head">
          <h2 class="card-title">欢迎回来</h2>
          <p class="card-desc">登录后进入运维控制台</p>
        </div>

        <el-tabs v-model="activeTab" stretch class="login-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form :model="loginForm" @submit.prevent>
              <el-form-item>
                <el-input v-model="loginForm.username" placeholder="用户名" size="large">
                  <template #prefix>
                    <svg class="field-icon" viewBox="0 0 24 24" width="16" height="16" fill="none"
                         stroke="currentColor" stroke-width="1.8">
                      <circle cx="12" cy="8" r="4" />
                      <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
                    </svg>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  show-password
                  @keyup.enter="handleLogin"
                >
                  <template #prefix>
                    <svg class="field-icon" viewBox="0 0 24 24" width="16" height="16" fill="none"
                         stroke="currentColor" stroke-width="1.8">
                      <rect x="4" y="10" width="16" height="10" rx="2" />
                      <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                    </svg>
                  </template>
                </el-input>
              </el-form-item>
              <el-button
                class="submit-btn"
                type="primary"
                size="large"
                :loading="loading"
                @click="handleLogin"
              >
                进入控制台
              </el-button>
            </el-form>
          </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" @submit.prevent>
            <el-form-item>
              <el-input v-model="registerForm.username" placeholder="用户名（唯一）" size="large" />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="密码（至少 6 位）"
                size="large"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="确认密码（再输入一次）"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
              <el-button
                class="submit-btn"
                type="success"
                size="large"
                :loading="loading"
                @click="handleRegister"
              >
                创建账号
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <p class="card-tip">默认管理员账号请联系项目管理员分配</p>
      </section>
    </div>

    <div class="page-footer">OpsAgent · 基于 AI Agent 的智能 Linux 运维与故障诊断平台</div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px 60px;
  background: radial-gradient(circle at 18% 12%, #0f2233 0%, #060a12 55%, #04070d 100%);
}

/* 网格叠加层 */
.grid-overlay {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(34, 211, 238, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.055) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at 45% 45%, rgba(0, 0, 0, 0.9), transparent 72%);
  pointer-events: none;
}

/* 缓动光斑 */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(95px);
  opacity: 0.45;
  pointer-events: none;
}

.orb-a {
  width: 430px;
  height: 430px;
  background: #0ea5e9;
  top: -130px;
  left: -90px;
  animation: ops-orb 18s ease-in-out infinite;
}

.orb-b {
  width: 400px;
  height: 400px;
  background: #6366f1;
  bottom: -150px;
  right: -70px;
  animation: ops-orb 22s ease-in-out infinite reverse;
}

/* ============ 两栏容器 ============ */
.hero {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1180px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 72px;
}

/* ============ 左栏 ============ */
.hero-left {
  flex: 1 1 auto;
  max-width: 560px;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #06121b;
  background: linear-gradient(135deg, #22d3ee, #6366f1);
  box-shadow: 0 10px 26px rgba(34, 211, 238, 0.35);
  animation: ops-float 5s ease-in-out infinite;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  font-size: 12px;
  color: var(--ops-text-dim);
}

.version-tag {
  margin-left: 4px;
  font-size: 11px;
  padding: 2px 9px;
  border-radius: 999px;
  color: var(--ops-primary);
  border: 1px solid rgba(34, 211, 238, 0.4);
  background: rgba(34, 211, 238, 0.08);
}

.hero-title {
  margin: 28px 0 16px;
  font-size: 42px;
  line-height: 1.22;
  font-weight: 800;
  letter-spacing: 1px;
  color: #dbe6f3;
}

.hero-title span {
  background: linear-gradient(110deg, #22d3ee 10%, #818cf8 60%, #22d3ee 95%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: ops-shimmer 6s linear infinite;
}

.hero-slogan {
  min-height: 24px;
  font-size: 14px;
  color: #9db1c8;
  letter-spacing: 0.5px;
}

.caret {
  margin-left: 2px;
  color: var(--ops-primary);
  animation: ops-caret 1s step-end infinite;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 30px 0 0;
}

.feature {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.feature:hover {
  border-color: rgba(34, 211, 238, 0.28);
  background: rgba(34, 211, 238, 0.06);
  transform: translateX(5px);
}

.ic {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ops-primary);
  background: rgba(34, 211, 238, 0.1);
  border: 1px solid rgba(34, 211, 238, 0.28);
}

.ft {
  font-size: 14.5px;
  color: #dbe6f3;
  font-weight: 600;
}

.fd {
  margin-top: 3px;
  font-size: 12.5px;
  color: #8fa3bb;
  line-height: 1.6;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.tech-tags span {
  font-size: 12px;
  padding: 5px 14px;
  border-radius: 999px;
  color: #a5b4c8;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.2);
  transition: all 0.3s ease;
}

.tech-tags span:hover {
  color: var(--ops-primary);
  border-color: rgba(34, 211, 238, 0.5);
  background: rgba(34, 211, 238, 0.1);
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(34, 211, 238, 0.18);
}

/* ============ 右栏：登录卡片 ============ */
.login-card {
  position: relative;
  flex: 0 0 auto;
  width: 390px;
  padding: 30px 30px 22px;
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  border-radius: 16px 16px 0 0;
  background: linear-gradient(90deg, transparent, var(--ops-primary), transparent);
}

.shake {
  animation: ops-shake 0.5s ease;
}

.card-head {
  margin-bottom: 6px;
  text-align: left;
}

.card-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.card-desc {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--ops-text-dim);
}

.login-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: rgba(148, 163, 184, 0.16);
}

.login-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  letter-spacing: 1px;
}

.field-icon {
  color: var(--ops-text-dim);
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  position: relative;
  overflow: hidden;
  letter-spacing: 3px;
  font-weight: 600;
  background-image: linear-gradient(120deg, #0891b2, #22d3ee, #6366f1);
  background-size: 220% auto;
  border: none;
  box-shadow: 0 10px 26px rgba(34, 211, 238, 0.26);
}

.submit-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 40%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.45), transparent);
  transform: skewX(-20deg);
  transition: left 0.6s ease;
}

.submit-btn:hover {
  background-position: right center;
  box-shadow: 0 12px 32px rgba(34, 211, 238, 0.4);
}

.submit-btn:hover::after {
  left: 120%;
}

.card-tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12px;
  color: rgba(148, 163, 184, 0.7);
}

/* ============ 页脚 ============ */
.page-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 20px;
  z-index: 2;
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.5px;
  color: rgba(148, 163, 184, 0.62);
  animation: ops-fade-in 1.2s ease 0.5s both;
}

/* ============ 小屏适配 ============ */
@media (max-width: 1024px) {
  .hero {
    gap: 36px;
  }
  .hero-title {
    font-size: 34px;
  }
}

@media (max-width: 860px) {
  .hero {
    flex-direction: column;
    gap: 28px;
  }
  .hero-left {
    max-width: 100%;
    text-align: center;
  }
  .brand-row {
    justify-content: center;
  }
  .feature-list {
    display: none; /* 小屏优先保证登录可用，隐藏介绍内容 */
  }
  .tech-tags {
    justify-content: center;
  }
  .login-card {
    width: 100%;
    max-width: 390px;
  }
}
</style>
