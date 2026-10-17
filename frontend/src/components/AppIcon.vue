<script setup lang="ts">
import type { AppIconName } from './icons'

/**
 * 应用图标集（内联 SVG）。
 *
 * <p>为什么用一个组件 + name 属性，而不是每个图标一个文件：
 * 图标本质是一段路径数据，拆成十几个文件只会让人找起来更累。
 * 统一在这里加，也保证了描边宽度、视框、取色方式完全一致 ——
 * 这是「设计语言统一」在图标层面的落点。
 *
 * <p>约定：
 * <ul>
 *   <li>24×24 视框，`stroke="currentColor"`，颜色由父级 `color` 决定</li>
 *   <li>描边 1.7px、圆角端点。不要单独覆盖这些值，否则图标粗细会不一致</li>
 *   <li>默认 `aria-hidden`：图标是装饰，可访问名称应由旁边的文字或 aria-label 提供</li>
 * </ul>
 *
 * <p>刻意不引入 @element-plus/icons-vue：只需十余个图标，多一个依赖不值得。
 */
withDefaults(
  defineProps<{
    name: AppIconName
    /** 像素尺寸，边长。默认 18 */
    size?: number
  }>(),
  { size: 18 },
)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <template v-if="name === 'dashboard'">
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.6" />
    </template>

    <template v-else-if="name === 'log'">
      <path d="M5 4h9l4 4v12H5z" />
      <path d="M14 4v4h4" />
      <path d="M8.5 12.5h7M8.5 16h5" />
    </template>

    <template v-else-if="name === 'download'">
      <path d="M12 4v10" />
      <path d="M8.2 10.4 12 14.2l3.8-3.8" />
      <path d="M5 18.5h14" />
    </template>

    <template v-else-if="name === 'user'">
      <circle cx="12" cy="8" r="3.6" />
      <path d="M5.2 19.8c.7-3.4 3.5-5.4 6.8-5.4s6.1 2 6.8 5.4" />
    </template>

    <template v-else-if="name === 'users'">
      <circle cx="9" cy="8" r="3.4" />
      <path d="M2.8 19.5c.6-3.3 3.2-5.2 6.2-5.2s5.6 1.9 6.2 5.2" />
      <path d="M16.5 5.2a3.2 3.2 0 0 1 0 6.2" />
      <path d="M18 14.4c1.9.5 3.2 2.2 3.5 4.6" />
    </template>

    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3" />
      <path
        d="M12 2.8v2.1M12 19.1v2.1M21.2 12h-2.1M4.9 12H2.8M18.5 5.5l-1.5 1.5M7 17l-1.5 1.5M18.5 18.5 17 17M7 7 5.5 5.5"
      />
    </template>

    <template v-else-if="name === 'logout'">
      <path d="M10 20H5.5A1.5 1.5 0 0 1 4 18.5v-13A1.5 1.5 0 0 1 5.5 4H10" />
      <path d="M15 8.5 18.5 12 15 15.5" />
      <path d="M18.5 12H9" />
    </template>

    <template v-else-if="name === 'collapse'">
      <rect x="3" y="4" width="18" height="16" rx="2.4" />
      <path d="M9.5 4v16" />
      <path d="M16.5 9.5 14 12l2.5 2.5" />
    </template>

    <template v-else-if="name === 'expand'">
      <rect x="3" y="4" width="18" height="16" rx="2.4" />
      <path d="M9.5 4v16" />
      <path d="M13.5 9.5 16 12l-2.5 2.5" />
    </template>

    <template v-else-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>

    <template v-else-if="name === 'refresh'">
      <path d="M20 11.5a8 8 0 1 0-1.6 5.6" />
      <path d="M20 5.5v6h-6" />
    </template>

    <template v-else-if="name === 'search'">
      <circle cx="11" cy="11" r="6.2" />
      <path d="m15.7 15.7 4.1 4.1" />
    </template>

    <template v-else-if="name === 'edit'">
      <path d="M4 20h4l10-10a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20Z" />
    </template>

    <template v-else-if="name === 'trash'">
      <path d="M4.5 7h15" />
      <path d="M9.5 7V5.2A1.2 1.2 0 0 1 10.7 4h2.6a1.2 1.2 0 0 1 1.2 1.2V7" />
      <path d="M6.5 7l.8 12.1a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4L17.5 7" />
    </template>

    <template v-else-if="name === 'database'">
      <ellipse cx="12" cy="6" rx="7.5" ry="3" />
      <path d="M4.5 6v12c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3V6" />
      <path d="M4.5 12c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3" />
    </template>

    <template v-else-if="name === 'shield'">
      <path d="M12 3.2 19 6v5.4c0 4.3-2.9 7.6-7 9.4-4.1-1.8-7-5.1-7-9.4V6l7-2.8Z" />
      <path d="m9.2 12 2 2 3.6-3.8" />
    </template>

    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="8.2" />
      <path d="M12 7.6V12l3 1.9" />
    </template>

    <template v-else>
      <path d="M5 12h13" />
      <path d="m13 7 5 5-5 5" />
    </template>
  </svg>
</template>
