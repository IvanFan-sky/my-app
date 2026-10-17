<script setup lang="ts">
/**
 * 应用外壳：左侧导航 + 顶栏 + 内容区。
 *
 * <p>只有**登录态页面**用它；登录/注册页直接渲染路由出口（见 App.vue）。
 *
 * <p>响应式策略：侧栏在窄屏（<900px）自动折叠成图标条。
 * 刻意不做「抽屉 + 遮罩」那一套 —— 本项目只有 3 个菜单项，
 * 图标条已经能完整导航，加遮罩反而多一层状态和一次点击。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import { useAppStore } from '@/stores/app'

const app = useAppStore()

/** 窄屏标记：由 resize 事件维护，用于自动折叠 */
const isNarrow = ref(false)
/** 窄屏下的实际折叠状态（不改用户在大屏上的偏好） */
const collapsed = computed(() => app.sidebarCollapsed || isNarrow.value)

function syncNarrow() {
  isNarrow.value = window.innerWidth < 900
}

let timer: number | undefined
function onResize() {
  // 防抖：拖窗口时 resize 会高频触发，直接 set 会反复重排
  window.clearTimeout(timer)
  timer = window.setTimeout(syncNarrow, 120)
}

onMounted(() => {
  syncNarrow()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.clearTimeout(timer)
})
</script>

<template>
  <div class="layout">
    <AppSidebar :collapsed="collapsed" />

    <div class="layout-main">
      <AppHeader :collapsed="collapsed" @toggle-sidebar="app.toggleSidebar()" />
      <main class="layout-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  /* 与登录页同一套底色：浅青底 + 极淡光晕，两个界面对得上 */
  background:
    radial-gradient(1100px 500px at 8% 0%, var(--app-glow-primary), transparent 62%),
    radial-gradient(900px 440px at 96% 6%, var(--app-glow-secondary), transparent 60%),
    var(--app-bg-page);
}

/* 侧栏固定，内容区自己滚动：长表格滚动时导航不动 */
.layout-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 100vh;
}

.layout-content {
  flex: 1;
  padding: var(--app-gutter);
  box-sizing: border-box;
  min-width: 0;
}

@media (max-width: 900px) {
  .layout-content {
    padding: 14px;
  }
}
</style>
