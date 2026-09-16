import request from './request'

/**
 * 与 AI 助手对话。
 * 会话需要登录令牌（axios 拦截器会自动带上）。
 */
/**
 * 与 AI 助手对话。
 * sessionId 传 null 表示新开会话，后端会返回新建的会话 id。
 */
export const chat = (message, sessionId = null) => request.post('/ai/chat', { message, sessionId })

/** 当前用户的会话列表 */
export const listSessions = () => request.get('/ai/sessions')

/** 某个会话的全部消息 */
export const getSessionMessages = (sessionId) => request.get(`/ai/sessions/${sessionId}/messages`)

/** 删除会话 */
export const deleteSession = (sessionId) => request.delete(`/ai/sessions/${sessionId}`)

/**
 * 查询 AI 用量与账户余额。
 * 返回：今日/累计的调用次数与 token 数，以及 DeepSeek 账户余额与告警标记。
 */
export const getUsage = () => request.get('/ai/usage')
