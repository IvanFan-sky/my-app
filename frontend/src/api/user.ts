import { request } from './http'

/** 与后端 com.example.myapp.dto.UserVO 对应（只声明前端用得到的字段） */
export interface User {
  id: number
  username: string
  email?: string | null
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

/** 对应后端 dto.UserForm。password 仅新增时必填，修改时留空表示不改密码 */
export interface UserForm {
  username: string
  password?: string
  email?: string
  age?: number
}

/** GET /api/health —— 在白名单里，无需登录 */
export function fetchHealth() {
  return request<Record<string, unknown>>({ url: '/health', method: 'get' })
}

/** 以下接口都需要登录 */
export function pageUsers(params: { username?: string; current?: number; size?: number }) {
  return request<PageResult<User>>({ url: '/users', method: 'get', params })
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
