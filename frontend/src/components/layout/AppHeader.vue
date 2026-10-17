<script setup lang="ts">
/**
 * 顶部栏：折叠按钮 + 面包屑 + 用户菜单。
 *
 * <p>面包屑由路由 meta 驱动（见 router/index.ts 的 meta.title / meta.parent），
 * 不在页面里硬编码 —— 否则每加一个页面都得记得回来改这里。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{ 'toggle-sidebar': [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 头像用「显示名」首字母：后端没有头像字段，
 * 色块 + 首字母比灰色占位图更好认。显示名未设置时回退到用户名。
 */
const initial = computed(() => (auth.displayName || '?').charAt(0).toUpperCase())

/** 面包屑：首页固定在前，当前页标题取自 meta.title */
const crumbs = computed(() => {
  const title = route.meta.title as string | undefined
  const list = [{ label: '首页', name: 'dashboard' }]
  // 当前是工作台时只显示「首页」，避免出现「首页 / 工作台」这种重复感
  if (title && route.name !== 'dashboard') {
    list.push({ label: title, name: String(route.name) })
  }
  return list
})

/** 下拉菜单命令分发 */
function handleCommand(command: string) {
  if (command === 'profile') {
    void router.push({ name: 'profile' })
    return
  }
  if (command === 'logout') {
    void handleLogout()
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出确认', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  auth.logout()
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="header">
    <button
      class="icon-btn"
      type="button"
      :title="props.collapsed ? '展开侧边栏' : '收起侧边栏'"
      :aria-label="props.collapsed ? '展开侧边栏' : '收起侧边栏'"
      @click="emit('toggle-sidebar')"
    >
      <AppIcon :name="props.collapsed ? 'expand' : 'collapse'" :size="18" />
    </button>

    <nav class="crumbs" aria-label="面包屑">
      <template v-for="(c, i) in crumbs" :key="c.name">
        <span v-if="i > 0" class="crumb-sep" aria-hidden="true">/</span>
        <RouterLink v-if="i < crumbs.length - 1" :to="{ name: c.name }" class="crumb-link">
          {{ c.label }}
        </RouterLink>
        <span v-else class="crumb-current">{{ c.label }}</span>
      </template>
    </nav>

    <div class="header-right">
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="user-btn" type="button">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ auth.displayName || '未登录' }}</span>
          <span v-if="auth.isAdmin" class="role-tag">管理员</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <AppIcon name="user" :size="15" />
              <span class="dd-text">个人中心</span>
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <AppIcon name="logout" :size="15" />
              <span class="dd-text">退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--app-header-h);
  padding: 0 16px;
  box-sizing: border-box;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  /* 内容滚动时顶栏保持可见 */
  position: sticky;
  top: 0;
  z-index: 10;
}

/* 图标按钮：自绘而不是用 el-button，避免 Element Plus 按钮的默认内边距影响对齐 */
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
  flex: none;
}

.icon-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--brand-ink);
}

.icon-btn:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

/* ---------------- 面包屑 ---------------- */
.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: var(--app-fs-secondary);
  min-width: 0;
}

.crumb-sep {
  color: var(--el-text-color-placeholder);
}

.crumb-link {
  color: var(--el-text-color-secondary);
  text-decoration: none;
  white-space: nowrap;
}

.crumb-link:hover {
  color: var(--brand-ink);
}

.crumb-current {
  color: var(--el-text-color-primary);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------------- 右侧用户区 ---------------- */
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.user-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: none;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.16s ease;
}

.user-btn:hover {
  background: var(--el-fill-color-light);
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--app-gradient-primary);
  flex: none;
}

.user-name {
  font-size: var(--app-fs-secondary);
  color: var(--el-text-color-regular);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dd-text {
  margin-left: 6px;
}

/* 管理员标签：用品牌色的浅底 + 深字，和状态胶囊同一套观感 */
.role-tag {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: var(--brand-ink);
  background: rgba(15, 118, 110, 0.1);
  flex: none;
}

/* 窄屏隐藏用户名只留头像，给面包屑腾空间 */
@media (max-width: 760px) {
  .user-name {
    display: none;
  }
}
</style>
