import request from './request'

/** 查询服务器列表（需要登录令牌） */
export const listServers = () => request.get('/servers')
