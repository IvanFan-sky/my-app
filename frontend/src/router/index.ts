import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由表。
 *
 * <p>结构上分成两棵：
 * <ul>
 *   <li><b>登录态页面</b>：挂在 AppLayout 下，共用侧栏 + 顶栏；</li>
 *   <li><b>免登录页面</b>：登录/注册，直接渲染，不套外壳。</li>
 * </ul>
 *
 * <p>登录态页面的 `meta.title` 会被顶栏面包屑读取，`meta` 里没有标题的页面
 * 面包屑只显示「首页」。新增页面时记得补 title，否则面包屑会缺一环。
 *
 * <p>页面组件用懒加载（`() => import(...)`）：首屏只需要登录页，
 * 把用户管理、设置等一起打进主包会让首屏白白变大。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      // 外壳本身不渲染内容，只提供侧栏 + 顶栏，内容由 children 填
      path: '/',
      component: AppLayout,
      // 访问 / 时重定向到工作台，避免「首页」和「工作台」两个名字并存
      redirect: { name: 'dashboard' },
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          // requiresAdmin：非管理员直接进不去。这只是体验层拦截，
          // 后端 AdminOnlyInterceptor 才是真正的权限边界。
          meta: { title: '用户管理', requiresAdmin: true },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
          meta: { title: '系统设置' },
        },
      ],
    },
    {
      // 兜底：未知路径回工作台。没有这一条时手打错地址会看到一片空白，
      // 用户会以为应用坏了。
      path: '/:pathMatch(.*)*',
      redirect: { name: 'dashboard' },
    },
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

  // 所有 public 页面（登录、注册）都是「未登录才该看」，
  // 已登录时统一回工作台，避免登录后手点后退又回到登录页
  if (to.meta.public) {
    return auth.isAuthenticated ? { name: 'dashboard' } : true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 管理员专属页面：非管理员别让他进去再吃一个 403。
  // 这里同样只是体验层 —— 真正的拦截在后端。
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    ElMessage.warning('该页面需要管理员权限')
    return { name: 'dashboard' }
  }
  return true
})

/** 切换页面后把标题同步到浏览器标签，方便同时开多个后台标签时区分 */
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · my-app` : 'my-app'
})

export default router
