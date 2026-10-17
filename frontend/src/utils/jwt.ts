/**
 * JWT 载荷解析工具。
 *
 * <p>**只用于展示**，绝不用于鉴权判断。原因：
 * <ol>
 *   <li>这里不验签 —— 任何人都能伪造一段 payload 骗过前端。</li>
 *   <li>真正有效的登录态由后端判断，令牌无效时接口会返回 401，
 *       由 http 层清登录态并跳登录页。</li>
 *   <li>把「是否过期」这类判断放前端只做体验优化（比如提前提示），
 *       不能当作安全边界。</li>
 * </ol>
 */
export interface JwtPayload {
  /** 用户名（后端写入的是标准 sub） */
  sub?: string
  /** 用户主键，后端以字符串存，避免 JSON 数字被解析成浮点 */
  uid?: string
  /** 签发时间，秒级时间戳 */
  iat?: number
  /** 过期时间，秒级时间戳 */
  exp?: number
}

/**
 * 解析 JWT 的 payload 段。
 *
 * <p>返回 null 表示格式不合法（不是三段、base64 解不开、不是 JSON）。
 * 调用方必须处理 null，不要假设一定有值。
 */
export function decodeJwtPayload(token: string): JwtPayload | null {
  if (!token) {
    return null
  }
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }
  try {
    // JWT 用的是 base64url：`-` `_` 要换回 `+` `/`，且长度需补齐到 4 的倍数，
    // 否则 atob 会直接抛错（这是手写解析 JWT 最常见的坑）
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    // atob 得到的是「二进制字符串」，中文等多字节字符必须先转成字节再按 UTF-8 解码，
    // 否则会变乱码。当前 payload 只有 ASCII，但写成通用形式避免将来踩坑。
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
    const json = new TextDecoder('utf-8').decode(bytes)
    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

/** 把「剩余秒数」格式化成可读文案 */
export function formatRemaining(seconds: number): string {
  if (seconds <= 0) {
    return '已过期'
  }
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) {
    return `${h} 小时 ${m} 分钟`
  }
  if (m > 0) {
    return `${m} 分钟`
  }
  return `${seconds} 秒`
}
