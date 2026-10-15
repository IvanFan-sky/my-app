import router from '@/router'
import { setTokenGetter, setUnauthorizedHandler } from '@/api/http'
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
}
