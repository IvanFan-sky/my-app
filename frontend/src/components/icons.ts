/**
 * 应用图标名联合类型。
 *
 * <p>单独放一个 .ts 文件，而不是从 AppIcon.vue 里导出：
 * `<script setup>` 是编译成组件 setup 函数的，不能 `export` 任何东西
 * （包括类型）。想共享类型只能放到普通模块里。
 *
 * <p>加新图标时改这里 + AppIcon.vue 的模板两处，
 * 其他组件因为引用的是这个类型，能立刻在编译期发现拼错的名字。
 */
export type AppIconName =
  | 'dashboard'
  | 'users'
  | 'user'
  | 'log'
  | 'download'
  | 'settings'
  | 'logout'
  | 'collapse'
  | 'expand'
  | 'plus'
  | 'refresh'
  | 'search'
  | 'edit'
  | 'trash'
  | 'database'
  | 'shield'
  | 'clock'
  | 'arrow-right'
