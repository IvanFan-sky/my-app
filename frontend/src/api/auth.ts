import { request } from './http'

/** 与后端 com.example.myapp.dto.UserVO 对应 */
export interface AuthUser {
  id: number
  username: string
  email?: string | null
  age?: number | null
  createdAt?: string
  updatedAt?: string
}

/** 与后端 com.example.myapp.dto.LoginResponse 对应 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export interface LoginPayload {
  username: string
  password: string
}

/** POST /api/auth/login —— 成功返回 JWT 与用户信息 */
export function login(data: LoginPayload) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

/** GET /api/auth/me —— 用本地 token 换取当前用户，用于刷新页面后恢复登录态 */
export function fetchMe() {
  return request<AuthUser>({ url: '/auth/me', method: 'get' })
}
