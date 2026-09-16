import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

/**
 * 页面路由表。
 *
 * meta.public = true 表示这个页面不需要登录就能访问。
 * 路由守卫（beforeEach）负责：没登录还想去受保护页面 → 踢回登录页。
 * 这和后端的 JWT 拦截器是"前后呼应"的：前端守卫管体验，后端拦截管安全。
 * 前端的判断永远不能被信任，真正的安全必须由后端保证。
 */
const routes = [
  { path: '/', redirect: '/servers' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/servers',
    name: 'servers',
    component: () => import('../views/ServerListView.vue')
  },
  {
    path: '/monitor/:id',
    name: 'monitor',
    component: () => import('../views/MonitorView.vue')
  },
  {
    path: '/agent',
    name: 'agent',
    component: () => import('../views/AgentView.vue')
  },
  {
    path: '/observability',
    name: 'observability',
    component: () => import('../views/ObservabilityView.vue')
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: () => import('../views/KnowledgeView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (!to.meta.public && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && userStore.token) {
    return { name: 'servers' }
  }
  return true
})

export default router
