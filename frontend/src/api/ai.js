import request from './request'

/**
 * 与 AI 助手对话。
 * 会话需要登录令牌（axios 拦截器会自动带上）。
 */
export const chat = (message) => request.post('/ai/chat', { message })

/**
 * 查询 AI 用量与账户余额。
 * 返回：今日/累计的调用次数与 token 数，以及 DeepSeek 账户余额与告警标记。
 */
export const getUsage = () => request.get('/ai/usage')
