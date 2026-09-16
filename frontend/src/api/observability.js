import request from './request'

/** Agent 可观测性统计总览 */
export const getObservabilitySummary = () => request.get('/observability/summary')
