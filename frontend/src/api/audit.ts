import { request } from './http'

/** 与后端 com.example.myapp.dto.AuditLogVO 对应 */
export interface AuditLog {
  id: number
  actorId?: number | null
  actorUsername?: string | null
  /** 枚举名，如 LOGIN / UPDATE_USER */
  action: string
  /** 中文标签，由后端给出（避免前后端各维护一份映射） */
  actionLabel?: string | null
  targetType?: string | null
  targetId?: number | null
  targetUsername?: string | null
  /** JSON 字符串，可能为空 */
  detail?: string | null
  ip?: string | null
  success: boolean
  createdAt?: string
}

/** 与后端 MyBatis-Plus Page 结构一致 */
export interface AuditLogPage {
  records: AuditLog[]
  total: number
  current: number
  size: number
}

export interface AuditLogQuery {
  keyword?: string
  action?: string
  current?: number
  size?: number
}

/** GET /api/audit-logs —— 仅管理员 */
export function pageAuditLogs(query: AuditLogQuery = {}) {
  return request<AuditLogPage>({
    url: '/audit-logs',
    method: 'get',
    params: {
      keyword: query.keyword || undefined,
      action: query.action || undefined,
      current: query.current ?? 1,
      size: query.size ?? 20,
    },
  })
}

/** GET /api/audit-logs/actions —— 筛选下拉的选项，由后端枚举生成 */
export function fetchAuditActions() {
  return request<Array<{ value: string; label: string }>>({
    url: '/audit-logs/actions',
    method: 'get',
  })
}
