/**
 * 低代码 OA 系统 —— 前端入口
 *
 * <p>初始化 Vue 3 应用，注册 Element Plus 组件库及全部图标。
 * 全局使用 Element Plus 的 default 尺寸。</p>
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'

const app = createApp(App)

// 注册所有 Element Plus 图标为全局组件，模板中可直接使用 <el-icon><Clock /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { size: 'default' })
app.mount('#app')
