/// <reference types="vite/client" />

// 让 .vue 文件在 TypeScript 里有类型
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 环境变量类型（只有 VITE_ 前缀的会暴露给浏览器）
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
