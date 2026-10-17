import { request } from './http'

/** 与后端 com.example.myapp.dto.UserVO 对应（只声明前端用得到的字段） */
export interface User {
  id: number
  username: string
  /** 显示名，未设置为 null */
  nickname?: string | null
  /** ADMIN / USER */
  role?: 'ADMIN' | 'USER'
  email?: string | null
  /** 个人简介 */
  bio?: string | null
  age?: number | null
  createdAt?: string
  updatedAt?: string
}

/** 与 MyBatis-Plus 的 Page 结构对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/**
 * 对应后端 dto.UserForm。password 仅新增时必填，修改时留空表示不改密码；
 * role 不传表示不改（编辑自己时前端会刻意不传，后端也拒绝改自己角色）。
 */
export interface UserForm {
  username: string
  password?: string
  nickname?: string
  email?: string
  bio?: string
  age?: number
  role?: 'ADMIN' | 'USER'
}

/** 对应后端 dto.UserStatsVO */
export interface UserStats {
  /** 未删除的用户总数 */
  totalUsers: number
  /** 缓存命中次数（自后端启动起，重启会归零） */
  cacheHitCount: number
  /** 缓存未命中次数 */
  cacheMissCount: number
  /** 命中率 0~1 */
  cacheHitRate: number
}

/** GET /api/health —— 在白名单里，无需登录 */
export function fetchHealth() {
  return request<Record<string, unknown>>({ url: '/health', method: 'get' })
}

/** 以下接口都需要登录 */

/** 分页查询。keyword 同时匹配用户名与邮箱，为空则查全部 */
export function pageUsers(params: { keyword?: string; current?: number; size?: number }) {
  return request<PageResult<User>>({ url: '/users', method: 'get', params })
}

/** 按 id 查询单个用户（后端走 Redis 缓存） */
export function getUser(id: number) {
  return request<User>({ url: `/users/${id}`, method: 'get' })
}

/** 概览统计 */
export function fetchUserStats() {
  return request<UserStats>({ url: '/users/stats', method: 'get' })
}

export function createUser(data: UserForm) {
  return request<User>({ url: '/users', method: 'post', data })
}

export function updateUser(id: number, data: UserForm) {
  return request<User>({ url: `/users/${id}`, method: 'put', data })
}

export function deleteUser(id: number) {
  return request<void>({ url: `/users/${id}`, method: 'delete' })
}