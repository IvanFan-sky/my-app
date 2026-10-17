<script setup lang="ts">
/**
 * 页面标题区：标题 + 描述 + 右侧操作位。
 *
 * <p>为什么要抽出来：如果一个页面的标题写在 el-card 的 header、另一个写在
 * 独立 div、第三个干脆没有，用户每换一页都要重新找「这页是干什么的」。
 * 统一成一个组件后，页面开头永远是这个结构，位置和字号不会漂。
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="ph-text">
      <h1 class="ph-title">{{ title }}</h1>
      <p v-if="description" class="ph-desc">{{ description }}</p>
    </div>
    <!-- 操作按钮由各页面通过插槽传入，没有就不占位 -->
    <div v-if="$slots.actions" class="ph-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.ph-title {
  margin: 0;
  font-size: var(--app-fs-page-title);
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.ph-desc {
  margin: 5px 0 0;
  font-size: var(--app-fs-secondary);
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.ph-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
