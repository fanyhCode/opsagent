import request from './request'

/** 待人工确认的操作列表 */
export const listPendingOperations = () => request.get('/operations/pending')

/** 操作审计记录 */
export const listAudits = (limit = 50) => request.get('/operations/audits', { params: { limit } })

/** 操作白名单与风险等级 */
export const listRegistry = () => request.get('/operations/registry')

/** 确认执行（需要 OPERATOR / ADMIN 角色） */
export const approveOperation = (id) => request.post(`/operations/${id}/approve`)

/** 拒绝执行 */
export const rejectOperation = (id) => request.post(`/operations/${id}/reject`)
