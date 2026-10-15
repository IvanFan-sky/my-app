<script setup lang="ts">
// 根组件：只负责整体布局与路由出口，不放业务逻辑。
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <span class="app-title">my-app</span>
      <span class="app-subtitle">Vue 3 + Spring Boot + MySQL + Redis</span>
    </el-header>

    <el-main>
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
}

.app-title {
  font-size: 18px;
  font-weight: 600;
}

.app-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
