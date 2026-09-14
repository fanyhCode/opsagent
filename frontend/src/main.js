import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 的暗色主题变量（配合 html 上的 dark 类生效）
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './styles/global.css'

/**
 * 应用入口。
 *
 * 这里做了四件事：
 * 1. createApp 创建 Vue 应用；
 * 2. 安装 Pinia（状态管理，用来保存登录用户和令牌）；
 * 3. 安装 Vue Router（页面路由）；
 * 4. 安装 Element Plus 并把界面语言设为中文。
 */
const app = createApp(App)

// 打开暗色主题：整个平台走"运维控制台"风格，暗色更符合使用场景，也更耐看
document.documentElement.classList.add('dark')

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
