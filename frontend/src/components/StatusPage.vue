<script setup lang="ts">
/**
 * 状态页（404 / 403）的共用外观。
 *
 * <p>为什么抽成一个组件：这两页长得几乎一样，只有数字和文案不同。各写一套的话，
 * 改个间距要改两处，很快就不一致了。
 *
 * <p>刻意**不用** el-result：它的默认插图与配色是 Element Plus 的风格，
 * 和本项目的青绿设计语言不搭；这里用纯 CSS 画，颜色全部取自 tokens.css。
 */
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'

defineProps<{
  /** 状态码，如 '404' */
  code: string
  title: string
  description: string
}>()

const router = useRouter()

/** 返回上一页；没有历史记录时（比如直接粘链接进来）回工作台 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push({ name: 'dashboard' })
}
</script>

<template>
  <div class="status-page">
    <div class="status-card">
      <div class="status-code" aria-hidden="true">{{ code }}</div>

      <h1 class="status-title">{{ title }}</h1>
      <p class="status-desc">{{ description }}</p>

      <div class="status-actions">
        <el-button
          class="app-btn-primary"
          type="primary"
          @click="router.push({ name: 'dashboard' })"
        >
          <AppIcon name="dashboard" :size="15" />
          <span class="btn-text">回工作台</span>
        </el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.status-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - var(--app-header-h) - 120px);
}

.status-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 48px 40px;
  max-width: 460px;
  border-radius: var(--app-radius-inner);
  background: #fff;
  box-shadow: var(--app-shadow-panel);
}

/* 大号数字本身就是视觉主体，用品牌渐变做文字填充，避免又插一张图 */
.status-code {
  font-size: 72px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 2px;
  font-variant-numeric: tabular-nums;
  background: var(--app-gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.status-title {
  margin: 20px 0 0;
  font-size: var(--app-fs-page-title);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.status-desc {
  margin: 10px 0 0;
  font-size: var(--app-fs-body);
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.status-actions {
  display: flex;
  gap: 10px;
  margin-top: 26px;
}

.btn-text {
  margin-left: 6px;
}

@media (max-width: 560px) {
  .status-card {
    padding: 32px 20px;
  }

  .status-code {
    font-size: 56px;
  }

  .status-actions {
    flex-direction: column;
    width: 100%;
  }
}
</style>
