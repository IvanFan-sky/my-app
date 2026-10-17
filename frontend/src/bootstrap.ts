import router from '@/router'
import {
  setForbiddenHandler,
  setRefreshHandler,
  setTokenGetter,
  setUnauthorizedHandler,
} from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/**
 * 把 api 层与 store / 路由接起来。
 *
 * <p>为什么不直接在 http.ts 里 import store 和 router：store 依赖 api 模块
 * （要调 login 接口），api 模块若反过来 import store 就形成循环依赖。
 * 循环依赖在打包时未必报错，却可能在运行时变成 undefined —— 这类问题很难查。
 * 所以让 api 层只暴露两个「注入点」，由本文件在启动时装配一次。
 *
 * <p>必须在应用挂载前调用：一旦有请求先发出去，令牌注入与 401 处理都还没生效。
 */
export function setupApiInterceptors() {
  const auth = useAuthStore()

  // 令牌的唯一来源是 store，避免多处各自读 localStorage
  setTokenGetter(() => auth.token)

  setUnauthorizedHandler(() => {
    // 已经是登录页就不要再跳，否则会自己跳自己
    if (router.currentRoute.value.name === 'login') {
      return
    }
    auth.logout()
    void router.replace({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  })

  /*
   * 续期：拦截器收到 401 时先来这里换一对新令牌，成功后自动重发原请求。
   * 去重（同一时刻只刷新一次）在 http.ts 里做，这里只负责「怎么换」。
   */
  setRefreshHandler(async () => auth.refresh())

  setForbiddenHandler(() => {
    /*
     * 已登录但角色不够：**不要**清登录态，也不要回登录页。
     * 用户身份是好的，问题在于这个资源不归他 —— 送去登录页会让他反复重登，
     * 还以为账号坏了。跳到 403 页把话说清楚。
     *
     * 已经在 403 页就不再跳，避免多个并发请求各自触发一次跳转。
     */
    if (router.currentRoute.value.name === 'forbidden') {
      return
    }
    void router.replace({ name: 'forbidden' })
  })
}
