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

/** 与后端 com.example.myapp.dto.RegisterForm 对应 */
export interface RegisterPayload {
  username: string
  password: string
  /** 选填；不填就不发这个字段（发空串与不发在语义上不同） */
  email?: string
}

/** POST /api/auth/login —— 成功返回 JWT 与用户信息 */
export function login(data: LoginPayload) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

/**
 * POST /api/auth/register —— 注册成功同样返回 JWT（注册即登录），
 * 因此响应结构与登录完全一致，前端两条路径可复用同一套处理。
 */
export function register(data: RegisterPayload) {
  return request<LoginResult>({ url: '/auth/register', method: 'post', data })
}

/** GET /api/auth/me —— 用本地 token 换取当前用户，用于刷新页面后恢复登录态 */
export function fetchMe() {
  return request<AuthUser>({ url: '/auth/me', method: 'get' })
}
