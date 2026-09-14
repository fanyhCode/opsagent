import request from './request'

/** 登录：成功后返回用户信息与令牌 */
export const login = (data) => request.post('/auth/login', data)

/** 注册 */
export const register = (data) => request.post('/auth/register', data)

/** 查询当前登录用户（用来验证令牌是否有效） */
export const getCurrentUser = () => request.get('/auth/me')
