import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { setupApiInterceptors } from './bootstrap'

// 注意：这里用的是 Element Plus 全量引入，开发期最省事。
// 上线前建议换成按需引入（unplugin-vue-components + unplugin-auto-import），
// 否则打包体积会明显偏大。
const app = createApp(App)

// Pinia 必须先于 setupApiInterceptors：后者要 useAuthStore()，
// 而 store 只有在 pinia 已安装（或作为参数传入）之后才能创建。
app.use(createPinia())
setupApiInterceptors()
app.use(router)
app.use(ElementPlus)

app.mount('#app')
