import { createApp } from 'vue'
import { createPinia } from 'pinia'

/*
 * Element Plus 采用**按需引入**（见 vite.config.ts 的 Components / AutoImport 插件）：
 * 模板里的 <el-*> 组件与脚本里的 ElMessage 都在构建时自动 import，样式随组件注入。
 *
 * 这里只显式引「基础样式」——它是 :root 上的 --el-* 变量定义（约 8 KB），
 * 不再是 361 KB 的全量 index.css。之所以必须显式引：
 *   组件级样式是按需注入的，如果连 :root 变量也交给它，变量的加载时机就会晚于
 *   tokens.css，于是我们覆盖的 --el-color-primary 会被默认的 #409eff 盖回去
 *   —— 表现是「主按钮又变回蓝色」，且只在某些页面复现，很难查。
 * 显式引一次，顺序就确定了：先默认变量，再项目令牌。
 */
import 'element-plus/theme-chalk/base.css'
// ElMessage / ElMessageBox 是「函数式」组件，不存在于模板里，按需插件不会替它们引样式，
// 只能显式引（两个文件加起来约 20 KB）。不引的话弹出来的提示框完全没有样式。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'

// 设计令牌必须排在 Element Plus 基础样式之后：它引用了 --el-* 变量，
// 且 .app-* 类需要能覆盖组件库的默认样式
import './styles/tokens.css'

import App from './App.vue'
import router from './router'
import { setupApiInterceptors } from './bootstrap'

const app = createApp(App)

// Pinia 必须先于 setupApiInterceptors：后者要 useAuthStore()，
// 而 store 只有在 pinia 已安装（或作为参数传入）之后才能创建。
app.use(createPinia())
setupApiInterceptors()
app.use(router)

/*
 * 不再 app.use(ElementPlus) 全量注册：
 * 组件与 v-loading 这类指令都由 unplugin-vue-components 在构建时按 SFC 实际用到的
 * 标签/指令逐个 import（ElementPlusResolver 默认就处理指令），
 * 全量注册会把所有组件都打进产物（改造前 index.js 1096 KB）。
 */

app.mount('#app')
