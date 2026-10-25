import http, { request } from './http'

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

/**
 * POST /api/users/batch-delete —— 批量逻辑删除，返回**实际删除的条数**。
 *
 * <p>为什么不返回传入条数：ids 里可能包含已被别人删掉的用户，
 * 回传入数量会让前端显示「已删除 10 个」而实际只删了 3 个。
 */
export function batchDeleteUsers(ids: number[]) {
  return request<number>({ url: '/users/batch-delete', method: 'post', data: { ids } })
}

/**
 * GET /api/users/export —— 导出 CSV（Blob）。
 *
 * <p>用 responseType: 'blob' 拿原始二进制，而不是走 request<T>()：
 * 那个函数会去剥 `{code,message,data}` 外壳，而 CSV 响应体根本不是 JSON。
 *
 * <p><b>必须自己校验 Content-Type</b>：blob 模式下 http 层的成功拦截器
 * 判断 `typeof body.code === 'number'` 恒为 false（Blob 上没有 code），
 * 于是一个「HTTP 200 + code 5000」的业务错误会被当成正常响应放过。
 * 结果就是浏览器把一段错误 JSON 存成 users-xxx.csv，用户双击打开是乱码，
 * 而且全程没有任何报错提示。
 */
export async function exportUsers(keyword?: string): Promise<Blob> {
  const response = await http.get('/users/export', {
    params: { keyword: keyword || undefined },
    responseType: 'blob',
  })
  const blob = response.data as Blob
  const contentType = String(response.headers['content-type'] ?? blob.type ?? '')
  if (!contentType.includes('text/csv')) {
    // 尽力把后端那句可读的 message 捞出来；捞不到就给一句兜底
    throw new Error((await readErrorMessage(blob)) ?? '导出失败，请稍后重试')
  }
  return blob
}

/** 从错误响应体里取 message。JSON 解析失败（比如返回了 HTML 错误页）时返回 null */
async function readErrorMessage(blob: Blob): Promise<string | null> {
  try {
    const body = JSON.parse(await blob.text()) as { message?: string }
    return body.message ?? null
  } catch {
    return null
  }
}
