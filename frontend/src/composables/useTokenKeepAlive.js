import { onMounted, onUnmounted } from 'vue'
import { getCurrentUser } from '../api/auth'
import { useUserStore } from '../stores/user'

/** 保活间隔：10 分钟 */
const KEEP_ALIVE_INTERVAL = 10 * 60 * 1000

/**
 * 令牌保活。
 *
 * 背景：后端令牌有效期只有 30 分钟，但开启滑动续期后，
 * 只要请求打到后端，快过期时会自动下发新令牌。
 * 问题是——用户可能开着页面看数据、不点任何按钮，30 分钟后就掉线了。
 *
 * 所以这里做两件事：
 * 1. 页面可见时，每 10 分钟静默调一次 /auth/me，顺带触发续期；
 * 2. 页面从后台切回前台时立刻补一次（应对"标签页在后台时定时器被浏览器降频"的情况）。
 *
 * 注意：页面隐藏时**不发请求**。人不在电脑前就没必要一直续令牌，
 * 这也是安全上的边界——既保证体验，又不让会话凭空延长。
 */
export function useTokenKeepAlive() {
  const userStore = useUserStore()
  let timer = null

  async function keepAlive() {
    // 没登录、或页面不可见时不发请求
    if (!userStore.token || document.hidden) {
      return
    }
    try {
      // 这个请求本身就会触发后端的滑动续期逻辑，返回值我们不需要
      await getCurrentUser()
    } catch (e) {
      // 401 由 axios 拦截器统一处理（清登录态 + 跳登录页），这里不用重复处理
    }
  }

  function startTimer() {
    stopTimer()
    timer = setInterval(keepAlive, KEEP_ALIVE_INTERVAL)
  }

  function stopTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function handleVisibilityChange() {
    if (!document.hidden) {
      // 回到前台立刻补一次，避免后台停留期间令牌已经过期
      keepAlive()
    }
  }

  onMounted(() => {
    startTimer()
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onUnmounted(() => {
    stopTimer()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })
}
