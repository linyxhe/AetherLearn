import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

// 创建应用并挂载：Pinia（状态）、Vue Router（路由）、Element Plus（组件库）
const app = createApp(App)
app.use(createPinia())
app.use(router)
// 统一 Element Plus 的日期、分页等组件为简体中文。
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
