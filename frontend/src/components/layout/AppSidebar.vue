<script setup lang="ts">
/**
 * 左侧导航。
 *
 * <p>菜单项由下方 NAV_ITEMS 这一份配置驱动，路由表只负责 meta 里的标题，
 * 两者不重复声明 —— 新增页面时改这里加一行即可。
 *
 * <p>折叠状态下只显示图标，文字用 v-show 而非 v-if：
 * 展开/收起要有个不闪烁的过渡，v-if 会让文字瞬间出现，观感很跳。
 * 同时给缩略状态补上 title 属性，鼠标悬停仍能看到名称。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  collapsed: boolean
}>()

const route = useRoute()

const auth = useAuthStore()

/**
 * 主菜单。name 与路由 name 必须一致，否则选中态匹配不上。
 *
 * <p>requiresAdmin 的项对普通用户**不显示**：留着让他点进去吃 403 只是徒增困惑。
 * 这只是界面层的隐藏，后端 AdminOnlyInterceptor 才是真正的权限边界。
 */
const NAV_ITEMS = [
  { name: 'dashboard', label: '工作台', icon: 'dashboard' as const, requiresAdmin: false },
  { name: 'users', label: '用户管理', icon: 'users' as const, requiresAdmin: true },
  { name: 'settings', label: '系统设置', icon: 'settings' as const, requiresAdmin: false },
  { name: 'profile', label: '个人中心', icon: 'user' as const, requiresAdmin: false },
]

/** 按当前用户权限过滤后的菜单 */
const navItems = computed(() => NAV_ITEMS.filter((item) => !item.requiresAdmin || auth.isAdmin))

/** 当前路由是否属于某一菜单项（子路由也能高亮父项） */
function isActive(name: string) {
  return route.name === name
}
</script>

<template>
  <aside class="nav" :class="{ 'is-collapsed': props.collapsed }">
    <!-- 品牌区：与登录页左栏同一套配色来源（--app-sidebar-bg 取自品牌渐变深端） -->
    <div class="nav-brand">
      <span class="nav-logo" aria-hidden="true">
        <AppIcon name="shield" :size="20" />
      </span>
      <span v-show="!props.collapsed" class="nav-brand-text">
        <strong>my-app</strong>
        <small>管理后台</small>
      </span>
    </div>

    <nav class="nav-menu" aria-label="主导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :to="{ name: item.name }"
        class="nav-item"
        :class="{ 'is-active': isActive(item.name) }"
        :title="props.collapsed ? item.label : undefined"
      >
        <span class="nav-item-icon"><AppIcon :name="item.icon" :size="18" /></span>
        <span v-show="!props.collapsed" class="nav-item-label">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <!-- 底部说明：开发期提示，正式项目可换成版本号 -->
    <div v-show="!props.collapsed" class="nav-foot">
      <span class="nav-foot-dot" aria-hidden="true" />
      <span>Vue 3 + Spring Boot</span>
    </div>
  </aside>
</template>

<style scoped>
.nav {
  display: flex;
  flex-direction: column;
  width: var(--app-sidebar-w);
  height: 100vh;
  /*
   * sticky 而不是固定 100vh 了事：页面整体滚动（内容区没有独立滚动条），
   * 不加 sticky 的话，表格一长侧栏就跟着滚出视野，用户得滚回顶部才能切页。
   * 用 sticky 比 position:fixed 更好 —— 侧栏仍参与 flex 布局，
   * 不需要给内容区补 margin-left，折叠动画也不会和 fixed 打架。
   */
  position: sticky;
  top: 0;
  background: var(--app-sidebar-bg);
  color: var(--app-nav-text);
  /* 折叠动画只作用宽度，避免 layout 抖动的属性一起过渡 */
  transition: width 0.22s ease;
  overflow: hidden;
  flex: none;
}

.nav.is-collapsed {
  width: var(--app-sidebar-w-collapsed);
}

/* ---------------- 品牌区 ---------------- */
.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--app-header-h);
  padding: 0 18px;
  box-sizing: border-box;
  flex: none;
  /* 与下方菜单分隔，但用极淡的线而不是硬边框 */
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.nav-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
  flex: none;
}

.nav-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
  /* 收起时文字会短暂超出，隐藏掉避免溢出 */
  overflow: hidden;
  white-space: nowrap;
}

.nav-brand-text strong {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.nav-brand-text small {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.62);
}

/* ---------------- 菜单 ---------------- */
.nav-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 10px;
  flex: 1;
  overflow-y: auto;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border-radius: 9px;
  color: var(--app-nav-text);
  text-decoration: none;
  font-size: var(--app-fs-body);
  white-space: nowrap;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
}

.nav-item:hover {
  background: var(--app-nav-hover-bg);
  color: #fff;
}

.nav-item.is-active {
  background: var(--app-nav-active-bg);
  color: var(--app-nav-text-active);
  font-weight: 600;
}

/* 选中项左侧亮条：用 ::before 而不是 border，
   这样不会影响文字基线，也不会在收起态残留 1px 空隙 */
.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--app-nav-marker);
}

.nav-item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  flex: none;
}

/* ---------------- 底部说明 ---------------- */
.nav-foot {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 14px 18px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
  overflow: hidden;
}

.nav-foot-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--app-nav-marker);
  flex: none;
}

/* 尊重「减少动态效果」 */
@media (prefers-reduced-motion: reduce) {
  .nav,
  .nav-item {
    transition: none;
  }
}
</style>
