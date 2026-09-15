import { defineStore } from 'pinia'

/**
 * 用户状态仓库（Pinia）。
 *
 * 保存两样东西：
 * - token：JWT 令牌，后续每个请求都要带上它；
 * - user：当前登录用户的信息（用户名、昵称、角色），用于界面显示。
 *
 * 为什么要存进 localStorage？因为浏览器刷新后内存里的数据会丢，
 * 存到 localStorage 才能做到"刷新页面仍然是登录状态"。
 *
 * 注意：localStorage 会被 XSS 攻击读取，所以项目后期可以考虑换成
 * HttpOnly Cookie + CSRF 防护，这是更安全的方案（面试可以聊这个权衡）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('opsagent_token') || '',
    user: JSON.parse(localStorage.getItem('opsagent_user') || 'null')
  }),

  getters: {
    isLogin: (state) => !!state.token
  },

  actions: {
    /** 登录成功后保存令牌与用户信息 */
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('opsagent_token', token)
      localStorage.setItem('opsagent_user', JSON.stringify(user))
    },

    /**
     * 静默更新令牌。
     * 后端开启滑动续期后，会在响应头 X-New-Token 里下发新令牌，
     * 前端拿到就替换掉旧的，用户完全无感知。
     */
    setToken(token) {
      this.token = token
      localStorage.setItem('opsagent_token', token)
    },

    /** 退出登录：清空内存与本地存储 */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('opsagent_token')
      localStorage.removeItem('opsagent_user')
    }
  }
})
