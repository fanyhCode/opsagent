import request from './request'

/** 实时采集一次指标（后端通过 SSH 执行只读命令） */
export const getOverview = (serverId) => request.get(`/monitor/${serverId}/overview`)

/** 查询历史采集记录，用于画趋势图 */
export const getHistory = (serverId, limit = 30) =>
  request.get(`/monitor/${serverId}/history`, { params: { limit } })
