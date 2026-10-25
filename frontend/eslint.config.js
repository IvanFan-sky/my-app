import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

/**
 * ESLint 扁平配置（ESLint 9+ 的格式，旧的 .eslintrc 已废弃）。
 *
 * <p>分工要清楚：
 * <ul>
 *   <li><b>ESLint 管「对不对」</b>：未使用的变量、误用 Vue API、模板里的错误写法、
 *       hooks 顺序等 —— 这些 vue-tsc 查不出来（它只查类型）。</li>
 *   <li><b>Prettier 管「好不好看」</b>：缩进、引号、换行。</li>
 * </ul>
 * 所以最后一项必须是 {@code eslint-config-prettier}：它把 ESLint 里所有**格式类**
 * 规则关掉。不关的话两个工具会互相打架 —— 一个要换行、一个不许换行，
 * 结果是谁最后跑谁赢，开发者一直在改同一处格式。
 */
export default [
  {
    // 不检查的目录：构建产物、依赖、IDE 配置
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'public/**'],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],

  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        // Vite 的编译期常量
        __VUE_OPTIONS_API__: 'readonly',
        __VUE_PROD_DEVTOOLS__: 'readonly',
        /*
         * unplugin-auto-import 在构建时注入的全局（见 vite.config.ts 的 AutoImport）。
         * 源码里不写 import 也能用，但 ESLint 不知道，会报 no-undef。
         * 类型声明在 src/auto-imports.d.ts，这里只补运行期的「已定义」信息。
         * 新增自动导入项时，两处都要加。
         */
        ElMessage: 'readonly',
        ElMessageBox: 'readonly',
      },
      parserOptions: {
        // .vue 文件里 <script lang="ts"> 需要 TS 解析器接手，
        // 外层仍由 vue-eslint-parser 负责拆分 SFC 结构
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      /*
       * 关掉「组件名必须多个单词」：
       * 本项目有 App.vue（根组件，Vue 官方模板就叫这个），
       * 以及按 create-vue 惯例命名的单文件页面。这条规则防的是「和 HTML 原生标签撞名」，
       * 而 App / Login / Register 都不撞，硬改名只会更别扭。
       */
      'vue/multi-word-component-names': 'off',

      /*
       * 未使用变量按 error，但放行下划线开头的占位：
       * 例如 (_, index) 里不用的第一个参数、解构时故意丢弃的字段。
       */
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' },
      ],

      // 模板里 v-for 缺 key 是真实性能问题（会整段重渲染），保持默认的 error
      // 但允许作用于字符串/数字以外的合法场景，这里不改默认值，仅留注释说明为何不关

      // 允许在 .vue 里使用多根节点（Vue 3 支持，且布局组件常用）
      'vue/no-multiple-template-root': 'off',
    },
  },

  {
    // 配置类文件与 scripts/ 下的构建脚本跑在 Node 环境里，不是浏览器。
    // 漏掉 scripts/ 会让 process / console 被报成 no-undef —— 那是环境没声明，不是代码有错
    files: ['*.config.{js,ts}', 'vite.config.ts', 'eslint.config.js', 'scripts/**/*.{js,mjs}'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },

  // 必须是最后一项：关闭所有与 Prettier 冲突的格式规则
  prettier,
]
