<script setup lang="ts">
// 根组件：只负责整体布局与路由出口，不放业务逻辑。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 登录页要占满整屏，不套应用外框，否则卡片会被 header 挤偏 */
const isLoginPage = computed(() => route.name === 'login')

function logout() {
  auth.logout()
  ElMessage.success('已退出登录')
  void router.replace({ name: 'login' })
}
</script>

<template>
  <!-- 登录页：直接渲染路由出口，不加外框 -->
  <RouterView v-if="isLoginPage" />

  <el-container v-else class="app-shell">
    <el-header class="app-header">
      <span class="app-title">my-app</span>
      <span class="app-subtitle">Vue 3 + Spring Boot + MySQL + Redis</span>

      <div class="header-right">
        <span v-if="auth.user" class="current-user">{{ auth.user.username }}</span>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </div>
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

/* margin-left:auto 把右侧内容推到最右，避免用绝对定位造成窄屏重叠 */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.current-user {
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
