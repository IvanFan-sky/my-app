import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'

/** 与后端 com.example.myapp.common.ApiResponse 一一对应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  // 开发期是 /api，由 Vite 代理转发到后端 8080（见 vite.config.ts）
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 后端用 code !== 0 表示业务失败。这里统一转成 rejected Promise，
// 于是调用方只写 try/catch，不必每处都判断 code。
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status as number | undefined
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
