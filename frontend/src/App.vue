<script setup lang="ts">
// 根组件：只负责整体布局与路由出口，不放业务逻辑。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 登录页与注册页要占满整屏，不套应用外框，否则卡片会被 header 挤偏 */
const isAuthPage = computed(() => route.name === 'login' || route.name === 'register')

function logout() {
  auth.logout()
  ElMessage.success('已退出登录')
  void router.replace({ name: 'login' })
}
</script>

<template>
  <!-- 登录/注册页：直接渲染路由出口，不加外框 -->
  <RouterView v-if="isAuthPage" />

  <el-container v-else class="app-shell">
    <el-header class="app-header">
      <div class="app-header-inner">
        <span class="app-logo" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
            <path
              d="M12 2.5 21 7v10l-9 4.5L3 17V7l9-4.5Z"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path
              d="M8.2 12.1 11 14.9l4.9-5.5"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <span class="app-title">my-app</span>
        <span class="app-subtitle">用户管理</span>

        <div class="header-right">
          <span v-if="auth.user" class="current-user">{{ auth.user.username }}</span>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </div>
    </el-header>

    <el-main class="app-main">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  /* 与登录页同一套底色思路：浅灰底 + 品牌色极淡的径向光晕，
     让登录页和内部页面看起来是同一个产品 */
  background:
    radial-gradient(1100px 520px at 12% 0%, rgba(109, 139, 255, 0.1), transparent 60%),
    var(--el-bg-color-page);
}

.app-header {
  display: flex;
  align-items: center;
  padding: 0;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

/* 头部内容与页面内容共用同一最大宽度，避免标题比表格更靠边 */
.app-header-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: var(--app-content-max);
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.app-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #fff;
  background: var(--app-gradient-primary);
  flex: none;
}

.app-title {
  font-size: 17px;
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

.app-main {
  padding: 20px;
}

@media (max-width: 760px) {
  /* 窄屏收起副标题，优先保证用户名与退出按钮不被挤掉 */
  .app-subtitle {
    display: none;
  }
}
</style>