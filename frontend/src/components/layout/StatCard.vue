<script setup lang="ts">
/**
 * 统计卡：标签 + 数值 + 图标。
 *
 * <p>刻意**没有**「环比 +12%」这类趋势位：后端没有时序数据，
 * 编一个假箭头出来比不放更糟 —— 用户会以为那是真的。
 * 等有了按天聚合的接口，再加 trend 插槽。
 *
 * <p>tone 决定图标底色，只用来做视觉区分，不承载语义（不表示"好/坏"）。
 */
import AppIcon from '@/components/AppIcon.vue'
import type { AppIconName } from '@/components/icons'

withDefaults(
  defineProps<{
    label: string
    /** 数值；null / undefined 时显示占位符而不是 0，避免把「没取到」说成「是 0」 */
    value: string | number | null | undefined
    icon: AppIconName
    /** 图标底色主题 */
    tone?: 'teal' | 'sky' | 'amber' | 'violet'
    /** 数值下方的补充说明 */
    hint?: string
  }>(),
  { tone: 'teal', hint: undefined },
)
</script>

<template>
  <div class="stat">
    <div class="stat-top">
      <span class="stat-label">{{ label }}</span>
      <span class="stat-icon" :class="`tone-${tone}`" aria-hidden="true">
        <AppIcon :name="icon" :size="16" />
      </span>
    </div>
    <div class="stat-value">{{ value ?? '—' }}</div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  border-radius: var(--app-radius-inner);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: var(--app-shadow-panel);
  /* 悬停微微抬起：一页里有多张卡时，给出「这些是可关注的数据」的反馈 */
  transition:
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.stat:hover {
  box-shadow: var(--app-shadow-card);
  transform: translateY(-1px);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.stat-label {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-secondary);
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex: none;
}

/* 四套浅底图标色：全部用低饱和背景 + 深一档前景，保证图标看得清 */
.tone-teal {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.1);
}

.tone-sky {
  color: #0e6f96;
  background: rgba(14, 111, 150, 0.1);
}

.tone-amber {
  color: #a16207;
  background: rgba(161, 98, 7, 0.1);
}

.tone-violet {
  color: #6d28d9;
  background: rgba(109, 40, 217, 0.1);
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
  color: var(--el-text-color-primary);
  /* 数字用等宽变体，避免不同位数跳动时左右晃 */
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-placeholder);
}

@media (prefers-reduced-motion: reduce) {
  .stat {
    transition: none;
  }

  .stat:hover {
    transform: none;
  }
}
</style>
