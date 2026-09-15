import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import router from '../router'

/**
 * 统一的 HTTP 请求实例。
 *
 * 两个拦截器是整个前端与后端打交道的"总闸门"：
 * 1. 请求拦截器：自动把令牌塞进请求头，业务代码就不用每次都手写；
 * 2. 响应拦截器：统一判断业务状态码、统一弹错误提示、统一处理登录失效。
 */
const request = axios.create({
  // 注意这里写的是 /api，会由 Vite 代理转发到后端 8080 端口
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：每个请求发出前，如果本地有令牌就带上
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    // 后端约定的格式：Authorization: Bearer <token>
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器：集中处理结果
request.interceptors.response.use(
  (response) => {
    // 滑动续期：后端在响应头里下发新令牌时，静默替换本地令牌
    // （axios 会把响应头名统一转成小写，所以这里写 x-new-token）
    const newToken = response.headers['x-new-token']
    if (newToken) {
      useUserStore().setToken(newToken)
    }

    const res = response.data
    // 后端约定 code=200 才是业务成功，其它都算失败
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 成功时直接把 {code,message,data} 返回，业务代码取 .data 即可
    return res
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      // 401 的两种来源：令牌无效/过期，或者会话超过最大时长（后端会把原因写在 message 里）
      const message = error.response.data?.message || '登录已过期，请重新登录'
      ElMessage.error(message)
      useUserStore().logout()
      router.push({ name: 'login' })
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
