import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      // 已登录的人不该再看到登录页
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    // 新页面加在这里，并同步建 src/views/XxxView.vue
    // 需要免登录访问的页面，给它加 meta: { public: true }
  ],
})

/**
 * 全局前置守卫：未登录一律去登录页。
 *
 * <p>这里只在**本地**判断有没有令牌，不调后端。原因：每次路由跳转都发一次
 * 校验请求会让页面切换明显变慢。真正的权限判定始终在后端 ——
 * 令牌无效时接口会返回 401，由 http.ts 的拦截器兜住并清登录态。
 * 所以这个守卫管的是「体验」，不是「安全」，不要把安全责任放这里。
 */
router.beforeEach((to) => {
  // 注意：useAuthStore 必须在守卫**内部**调用。模块顶层调用会在 pinia 安装前执行，
  // 直接报 "getActivePinia() was called but there was no active Pinia"。
  const auth = useAuthStore()

  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

export default router
