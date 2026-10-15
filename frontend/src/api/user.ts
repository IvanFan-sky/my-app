import { request } from './http'

/** 与后端 com.example.myapp.entity.User 对应（只声明前端用得到的字段） */
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

export interface UserForm {
  username: string
  email?: string
  age?: number
}

export function fetchHealth() {
  return request<Record<string, unknown>>({ url: '/health', method: 'get' })
}

export function pageUsers(params: { username?: string; current?: number; size?: number }) {
  return request<PageResult<User>>({ url: '/users', method: 'get', params })
}

export function createUser(data: UserForm) {
  return request<User>({ url: '/users', method: 'post', data })
}

export function deleteUser(id: number) {
  return request<void>({ url: `/users/${id}`, method: 'delete' })
}
