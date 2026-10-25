import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 导出接口的响应类型校验。
 *
 * <p><b>为什么值得测</b>：`responseType: 'blob'` 时 http 层的成功拦截器
 * 判断 `typeof body.code === 'number'` 恒为 false（Blob 上没有 code），
 * 业务错误会被当成正常响应放过。不校验的话，后端返回一段错误 JSON，
 * 浏览器会把它存成 users-xxx.csv —— 用户双击打开是乱码，且全程没有提示。
 */

const mocks = vi.hoisted(() => ({ get: vi.fn(), request: vi.fn() }))

vi.mock('@/api/http', () => ({
  default: { get: mocks.get },
  request: mocks.request,
}))

const { exportUsers } = await import('@/api/user')

/** 造一个只实现 text() 的假 Blob，避免依赖 jsdom 对 Blob 的实现细节 */
function fakeBlob(content: string, type = 'application/json'): Blob {
  return { type, text: async () => content } as unknown as Blob
}

describe('exportUsers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('Content-Type 是 text/csv 时返回 Blob，文件名优先取响应头', async () => {
    const blob = fakeBlob('id,username\r\n1,alice', 'text/csv;charset=UTF-8')
    mocks.get.mockResolvedValue({
      data: blob,
      headers: {
        'content-type': 'text/csv;charset=UTF-8',
        'content-disposition': 'attachment; filename="users-20261025-224225.csv"',
      },
    })

    const result = await exportUsers('alice')
    expect(result.blob).toBe(blob)
    // 后端按服务端时钟拼的文件名，前端不该自己再拼一个（时区会差 8 小时）
    expect(result.filename).toBe('users-20261025-224225.csv')
  })

  it('响应头里没有文件名时返回 null，交给调用方兜底', async () => {
    mocks.get.mockResolvedValue({
      data: fakeBlob('id\r\n', 'text/csv;charset=UTF-8'),
      headers: { 'content-type': 'text/csv;charset=UTF-8' },
    })

    const result = await exportUsers()
    expect(result.blob).toBeDefined()
    expect(result.filename).toBeNull()
  })

  it('后端返回 JSON 错误时抛错，并把后端的 message 透出来', async () => {
    mocks.get.mockResolvedValue({
      data: fakeBlob(JSON.stringify({ code: 5000, message: '服务器内部错误' })),
      headers: { 'content-type': 'application/json' },
    })

    await expect(exportUsers()).rejects.toThrow('服务器内部错误')
  })

  it('响应体不是 JSON 时给一句兜底提示，而不是把原始内容喷给用户', async () => {
    mocks.get.mockResolvedValue({
      data: fakeBlob('<html>502 Bad Gateway</html>', 'text/html'),
      headers: { 'content-type': 'text/html' },
    })

    await expect(exportUsers()).rejects.toThrow('导出失败，请稍后重试')
  })
})
