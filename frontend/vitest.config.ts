import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 单元测试配置（与 vite.config.ts 分开）。
 *
 * <p><b>为什么不复用 vite.config.ts</b>：那里挂着 Element Plus 的按需引入插件，
 * 而测试只跑纯逻辑（http 拦截器、store、api 封装），不需要解析组件与样式 ——
 * 把那套插件带进测试环境只会让启动更慢、失败原因更难读。组件测试确实需要
 * `@vitejs/plugin-vue`，所以只引这一个。
 *
 * <p>测试文件放在 `src/**\/__tests__/*.spec.ts`，与源码同目录：
 * 改动某个模块时，它的测试就在旁边，不容易忘。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // 与 tsconfig 的 paths 保持一致，否则测试里 import '@/api/http' 会解析失败
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    // 需要 localStorage / Blob：store 与导出逻辑都依赖浏览器 API
    environment: 'jsdom',
    include: ['src/**/__tests__/*.spec.ts'],
    // 不开 globals：测试文件显式 import describe/it/expect，
    // 好处是 tsconfig 不用为测试加 types，也不会出现「变量从哪来的」疑问
    globals: false,
  },
})
