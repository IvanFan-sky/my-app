import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'

// 注意：这里用的是 Element Plus 全量引入，开发期最省事。
// 上线前建议换成按需引入（unplugin-vue-components + unplugin-auto-import），
// 否则打包体积会明显偏大。
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
