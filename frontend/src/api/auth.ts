import { request } from './http'

/** 角色常量，与后端 com.example.myapp.common.UserRole 对应 */
export const ROLE_ADMIN = 'ADMIN'
export const ROLE_USER = 'USER'
export type UserRole = typeof ROLE_ADMIN | typeof ROLE_USER

/** 与后端 com.example.myapp.dto.UserVO 对应 */
export interface AuthUser {
  id: number
  username: string
  /** 显示名，未设置为 null */
  nickname?: string | null
  /** ADMIN / USER。前端用它控制菜单与路由，**真正的权限判定在后端** */
  role?: UserRole
  email?: string | null
  /** 个人简介 */
  bio?: string | null
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

/**
 * 与后端 com.example.myapp.dto.ProfileForm 对应。
 *
 * <p>注意**没有** username / role / password：后端的 DTO 里也没有这些字段，
 * 所以即使请求里塞进去也不会生效（Jackson 忽略未知属性）。类型上也挡一层，
 * 免得有人误以为前端能改角色。
 */
export interface ProfilePayload {
  nickname?: string
  email?: string
  bio?: string
  age?: number
}

/** GET /api/auth/me —— 用本地 token 换取当前用户，用于刷新页面后恢复登录态 */
export function fetchMe() {
  return request<AuthUser>({ url: '/auth/me', method: 'get' })
}

/**
 * PUT /api/auth/profile —— 修改**自己**的资料。
 *
 * <p>路径里没有 id：身份只从令牌取，越权在结构上不成立。
 */
export function updateProfile(data: ProfilePayload) {
  return request<AuthUser>({ url: '/auth/profile', method: 'put', data })
}

/** PUT /api/auth/password —— 修改自己的密码，必须提供原密码 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request<void>({ url: '/auth/password', method: 'put', data })
}
