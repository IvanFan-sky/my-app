/// <reference types="vite/client" />

// 让 .vue 文件在 TypeScript 里有类型
//
// 这里的泛型参数是 DefineComponent<Props, RawBindings, D>：
// - 原来写 <{}, {}, any>：`{}` 会放行 0 / '' 这类非对象值，`any` 则关掉了整个返回类型检查，
//   两者都被 @typescript-eslint 判为危险写法（no-empty-object-type / no-explicit-any）。
// - 换成 Record<string, unknown> + unknown 后语义不变（这是个通用兜底声明，
//   真实 props 类型由 vue-tsc 从 SFC 里推出来，不走这里），但类型检查不再被 any 打开缺口。
declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

// 环境变量类型（只有 VITE_ 前缀的会暴露给浏览器）
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
