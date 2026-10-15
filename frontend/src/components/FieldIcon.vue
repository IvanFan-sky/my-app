<script setup lang="ts">
/**
 * 登录 / 注册页共用的输入框图标。
 *
 * <p>用内联 SVG 而不是 @element-plus/icons-vue：只为几个图标引一个依赖不划算，
 * 而且内联 SVG 能用 currentColor 直接跟随主题色。
 *
 * <p>aria-hidden：图标纯装饰，输入框已有 label，读屏软件重复朗读反而啰嗦。
 */
defineProps<{
  name: 'user' | 'lock' | 'mail'
}>()
</script>

<template>
  <svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <template v-if="name === 'user'">
      <circle cx="12" cy="8" r="3.6" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M4.8 20c.7-3.6 3.6-5.6 7.2-5.6s6.5 2 7.2 5.6"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </template>

    <template v-else-if="name === 'lock'">
      <rect x="4.5" y="10" width="15" height="10" rx="2.4" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M8.2 10V7.6a3.8 3.8 0 0 1 7.6 0V10"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </template>

    <template v-else>
      <rect x="3.5" y="5.5" width="17" height="13" rx="2.4" stroke="currentColor" stroke-width="1.6" />
      <path
        d="m5.2 8.2 5.6 4.2a2 2 0 0 0 2.4 0l5.6-4.2"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
  </svg>
</template>
