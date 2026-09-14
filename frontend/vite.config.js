import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置。
 *
 * 重点看 server.proxy：前端开发服务器跑在 5173 端口，后端在 8080 端口，
 * 浏览器直接请求 8080 会触发跨域（CORS）限制。
 * 配置代理之后，前端代码里统一写 "/api"，请求先发给 5173，
 * 由 Vite 转发给 8080——浏览器视角下这是同源请求，CORS 问题就不存在了。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
