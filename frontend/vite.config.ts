import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),

    /**
     * Element Plus 按需引入（两个插件各管一半，缺一不可）：
     *
     * - Components：模板里写的 <el-button> 等在**构建时**被自动 import，
     *   并把该组件的 CSS 一并带上。没有它就只能 app.use(ElementPlus) 全量注册，
     *   打出来的 chunk 会包含全部组件与全部样式（改造前实测 index.js 1096 KB + CSS 360 KB）。
     * - AutoImport：脚本里用的 ElMessage / ElMessageBox 属于函数式 API，
     *   不出现在模板里，Components 扫不到，由它负责注入 import 与样式。
     *
     * dts 生成的 .d.ts 必须提交进仓库：`pnpm build` 先跑 vue-tsc 再 vite build，
     * 类型声明不存在时类型检查会因「找不到 ElMessage」直接失败。
     */
    AutoImport({
      /*
       * 脚本里用的 ElMessage / ElMessageBox 在这里解析成**深路径**而不是包根。
       *
       * 需要说明的是：这**不是**让体积降下来的原因（真正的坑在下面的 manualChunks，
       * 见那里的注释）。包根 `element-plus/es` 本身是可以被 tree-shaking 摇掉的，
       * 但前提是没有被强制分到具名 chunk。
       *
       * 走深路径的理由是另一层保障：函数式 API 不进模板，不参与组件的按需收集，
       * 如果哪天分包配置又被改坏，这里的桶文件引用会成为第二个漏点。
       * 代价是需要显式引它们俩的样式 —— 已放在 main.ts 里，和 base.css 顺序可控。
       */
      imports: [
        {
          'element-plus/es/components/message/index': ['ElMessage'],
          'element-plus/es/components/message-box/index': ['ElMessageBox'],
        },
      ],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      /*
       * 组件 + 指令（v-loading）都由解析器按需注入。
       * 注意 ElementPlusResolver 的 directives 默认就是 true，
       * 所以 v-loading 不需要在 main.ts 里再全局注册一次 —— 重复注册不会报错，
       * 但会让「到底谁负责这个指令」变得含糊，且多引一份 loading 的 service。
       */
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        /**
         * 只给「体积小、几乎不变、且全部都会被用到」的框架代码单独分包。
         *
         * <p><b>这里有一个必须写下来的坑</b>：Rollup 的 manualChunks 一旦把某个模块
         * 分到具名 chunk，它**就一定会被打包**——哪怕代码里根本没用到。
         * 最早我按 `id.includes('element-plus')` 通配分配，结果整个
         * `element-plus/es` 桶文件里 80 多个组件全被强制塞进来，
         * 产物里出现了 ElTransfer / ElTree / ElUpload 这些完全没用的东西，
         * element-plus 那个 chunk 高达 810 KB —— **tree-shaking 被自己的分包配置废掉了**。
         *
         * <p>去掉分包后同样的源码只有 364 KB。改为只对下面这几个库分包：
         * 它们全部会被加载、体积稳定，拆出来能让业务代码改动时不影响缓存。
         * 其余依赖（element-plus 及其按需组件）交给 Rollup 自动拆成
         * el-input / el-table 这类小 chunk，由各页面按需加载。
         */
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          // 只匹配包名边界，避免误伤（例如 'vue' 不能匹配到 'vue-tsc' 之类）
          const normalised = id.replace(/\\/g, '/')
          const is = (pkg: string) => normalised.includes(`/node_modules/${pkg}/`)
          if (
            is('vue') ||
            is('@vue/runtime-core') ||
            is('@vue/shared') ||
            is('vue-router') ||
            is('pinia')
          ) {
            return 'vendor-vue'
          }
          return undefined
        },
      },
    },
  },
  server: {
    // 显式绑定 IPv4 回环。默认的 'localhost' 会先解析到 IPv6 的 ::1，
    // 在部分受限环境里绑定 ::1 会被拒绝（listen EACCES ::1:5173）。
    host: '127.0.0.1',
    // 刻意不用 Vite 默认的 5173：本机 Hyper-V 预留了 4990–5188 端口段
    // （netsh interface ipv4 show excludedportrange protocol tcp 可查），
    // 5173/5174 都在其中，绑定必然报 EACCES。3000 实测可用。
    port: 3000,
    // 代理：前端写 /api/xxx，由 Vite 转发到后端 8080。
    // 这样浏览器里是同源请求，开发期不需要处理 CORS。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
