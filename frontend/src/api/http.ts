import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'

/** 与后端 com.example.myapp.common.ApiResponse 一一对应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 与后端 com.example.myapp.common.ErrorCode.UNAUTHORIZED 对应 */
export const CODE_UNAUTHORIZED = 4010

/**
 * 后端返回「未认证」时（HTTP 401）的回调。
 *
 * <p>用注册回调而不是在这里 import auth store，是为了避免循环依赖：
 * store 要调 api 模块，api 模块若反过来 import store 就成环了。
 * 由 main.ts 在启动时注册一次，把「清登录态 + 跳登录页」交给路由层决定。
 */
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler
}

/**
 * 读取令牌的函数，同样由 main.ts 注入。
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

// 请求拦截器：带上令牌。后端 AuthInterceptor 只认 Authorization: Bearer <token>
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

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
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status as number | undefined
    if (status === 401) {
      // 令牌缺失/过期/无效。交给路由层清理登录态并跳转，
      // 不在拦截器里直接改 location，否则会丢掉当前路径、也无法避免连环跳转。
      onUnauthorized?.()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }
    const message = status ? `HTTP ${status}` : ((error?.message as string) || '网络错误')
    return Promise.reject(new Error(message))
  },
)

/** 发请求并直接返回业务数据（已剥掉 ApiResponse 外壳） */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}

export default http
