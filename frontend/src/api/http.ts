import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'

/** 与后端 com.example.myapp.common.ApiResponse 一一对应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 与后端 com.example.myapp.common.ErrorCode.UNAUTHORIZED 对应 */
export const CODE_UNAUTHORIZED = 4010

/** 与后端 com.example.myapp.common.ErrorCode.FORBIDDEN 对应（已登录但角色不够） */
export const CODE_FORBIDDEN = 4030

/**
 * 给请求配置加一个自定义标记。
 *
 * <p>用模块增强而不是 `config as any`：后者会让这个字段在别处拼错也不报错，
 * 而拼错的后果是「续期请求自己触发续期」→ 无限递归。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    /** true 表示这个请求本身是续期请求，401 时不要再触发续期 */
    skipAuthRefresh?: boolean
  }
}

/**
 * 后端返回「未认证」时（HTTP 401）的回调。
 *
 * <p>用注册回调而不是在这里 import auth store，是为了避免循环依赖：
 * store 要调 api 模块，api 模块若反过来 import store 就成环了。
 * 由 bootstrap.ts 在启动时注册一次，把「清登录态 + 跳登录页」交给路由层决定。
 */
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler
}

/**
 * 后端返回「已登录但无权限」时（HTTP 403 / code 4030）的回调。
 *
 * <p>和 401 分开是因为处理方式完全不同：401 要清登录态回登录页，
 * 403 不能动登录态 —— 用户身份是好的，把他踢去登录页只会让他反复重登。
 * 这里交给路由层跳 403 页，说明「这个页面不归你」。
 */
type ForbiddenHandler = () => void
let onForbidden: ForbiddenHandler | null = null

export function setForbiddenHandler(handler: ForbiddenHandler) {
  onForbidden = handler
}

/**
 * 续期回调：去换一对新令牌。
 *
 * <p>返回 true 表示已换到新令牌（此时 token getter 也会返回新值），
 * 拦截器会把原请求重发一次；返回 false 表示续期失败，按未登录处理。
 */
type RefreshHandler = () => Promise<boolean>
let onRefresh: RefreshHandler | null = null

export function setRefreshHandler(handler: RefreshHandler) {
  onRefresh = handler
}

/**
 * 读取令牌的函数，同样由 bootstrap.ts 注入。
 *
 * <p>不让本模块直接读 localStorage，是为了「谁持有登录态」只有 store 一个来源，
 * 避免出现两处各自读 key、改 key 时漏改一处的情况。
 */
type TokenGetter = () => string
let getToken: TokenGetter = () => ''

export function setTokenGetter(getter: TokenGetter) {
  getToken = getter
}

const http = axios.create({
  // 开发期是 /api，由 Vite 代理转发到后端 8080（见 vite.config.ts）
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 请求拦截器：带上令牌。后端 AuthInterceptor 只认 Authorization: Bearer <access>
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/**
 * 单飞（single-flight）续期。
 *
 * <p>为什么必须去重：一个页面同时发 5 个请求、令牌刚好过期，5 个都会拿到 401。
 * 若各自去续期，就会有 5 次刷新、5 组新令牌，最后只有最后签发的那组有效，
 * 其余立刻因轮换/版本失配而作废 —— 用户会看到莫名其妙的「登录已失效」。
 * 这里让它们共用同一个 Promise，只刷新一次。
 */
let refreshing: Promise<boolean> | null = null

function ensureRefreshed(): Promise<boolean> {
  if (!onRefresh) {
    return Promise.resolve(false)
  }
  if (!refreshing) {
    refreshing = onRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

/** 已重试过的请求标记。用 WeakSet 而不是往 config 上挂字段，避免污染请求对象 */
const retried = new WeakSet<object>()

// 后端用 code !== 0 表示业务失败。这里统一转成 rejected Promise，
// 于是调用方只写 try/catch，不必每处都判断 code。
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    if (body && typeof body.code === 'number' && body.code !== 0) {
      // 后端鉴权失败时除了 code 还会给 HTTP 401（见 AuthInterceptor），
      // 但错误响应有时不经过上面那条分支，这里一并兜住
      if (body.code === CODE_UNAUTHORIZED) {
        onUnauthorized?.()
      } else if (body.code === CODE_FORBIDDEN) {
        onForbidden?.()
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError) => {
    const status = error.response?.status
    const original = error.config as InternalAxiosRequestConfig | undefined

    if (status === 401) {
      /*
       * 401 有两种可能：
       *   1) access 过期 —— 可以用 refresh 换回来，用户无感；
       *   2) 令牌被作废（改过密码/角色，token_version 不匹配）—— 续期也会失败。
       * 所以先尝试续期一次；续期失败、或这个请求本身不该续期，才按未登录处理。
       */
      const canRetry = original != null && !original.skipAuthRefresh && !retried.has(original)

      if (canRetry) {
        retried.add(original)
        const refreshed = await ensureRefreshed()
        if (refreshed) {
          // 重发原请求。新令牌由请求拦截器从 store 重新取，这里不用手动改 header。
          return http.request(original)
        }
      }

      // 续期失败 / 不该续期：交给路由层清理登录态并跳转，
      // 不在拦截器里直接改 location，否则会丢掉当前路径、也无法避免连环跳转。
      onUnauthorized?.()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }

    if (status === 403) {
      // 已登录但角色不够（见 RoleInterceptor）。不清登录态，只跳 403 页。
      onForbidden?.()
      return Promise.reject(new Error('没有访问该资源的权限'))
    }

    const message = status ? `HTTP ${status}` : (error.message ?? '网络错误')
    return Promise.reject(new Error(message))
  },
)

/** 发请求并直接返回业务数据（已剥掉 ApiResponse 外壳） */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}

export default http
