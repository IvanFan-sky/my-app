import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 登录态 store 的会话恢复与清理。
 *
 * <p><b>为什么值得测</b>：`restore()` 决定「刷新页面后还算不算登录」。
 * 它写错的表现很隐蔽 —— 令牌过期了却不清会话，页面会停在半登录状态、
 * 每个请求都 401；或者反过来，令牌明明有效却被清掉，用户每次刷新都要重登。
 * 这两头都只能靠时序与本地存储的配合来保证，手测很难覆盖全。
 */

const mocks = vi.hoisted(() => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  refreshTokens: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  ROLE_ADMIN: 'ADMIN',
  ROLE_USER: 'USER',
  ...mocks,
}))

const { useAuthStore } = await import('@/stores/auth')

const TOKEN_KEY = 'my-app.token'
const REFRESH_KEY = 'my-app.refresh'
const USER_KEY = 'my-app.user'

const USER = { id: 1, username: 'alice', nickname: '爱丽丝', role: 'ADMIN' }

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('restore：令牌有效时恢复会话并写回用户信息', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    mocks.fetchMe.mockResolvedValue(USER)

    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)

    await expect(auth.restore()).resolves.toBe(true)

    expect(auth.user?.username).toBe('alice')
    expect(auth.isAdmin).toBe(true)
    expect(auth.displayName).toBe('爱丽丝')
    // 用户信息要落盘，否则刷新后头部会闪一下空值
    expect(localStorage.getItem(USER_KEY)).toContain('alice')
  })

  it('restore：令牌失效时清空会话（内存与 localStorage 都要清）', async () => {
    localStorage.setItem(TOKEN_KEY, 'expired-token')
    localStorage.setItem(REFRESH_KEY, 'expired-refresh')
    localStorage.setItem(USER_KEY, JSON.stringify(USER))
    mocks.fetchMe.mockRejectedValue(new Error('登录已失效'))

    const auth = useAuthStore()
    await expect(auth.restore()).resolves.toBe(false)

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
    // 只清内存不清存储的话，下次刷新又会被「恢复」成一个假登录态
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(REFRESH_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('restore：没有令牌时直接返回 false，不发请求', async () => {
    const auth = useAuthStore()
    await expect(auth.restore()).resolves.toBe(false)
    expect(mocks.fetchMe).not.toHaveBeenCalled()
  })

  it('refresh：没有 refresh token 时返回 false，不调接口', async () => {
    const auth = useAuthStore()
    await expect(auth.refresh()).resolves.toBe(false)
    expect(mocks.refreshTokens).not.toHaveBeenCalled()
  })

  it('refresh：成功时把新令牌对写进会话', async () => {
    localStorage.setItem(REFRESH_KEY, 'old-refresh')
    mocks.refreshTokens.mockResolvedValue({
      token: 'new-access',
      refreshToken: 'new-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: USER,
    })

    const auth = useAuthStore()
    await expect(auth.refresh()).resolves.toBe(true)

    expect(auth.token).toBe('new-access')
    expect(localStorage.getItem(TOKEN_KEY)).toBe('new-access')
    expect(localStorage.getItem(REFRESH_KEY)).toBe('new-refresh')
  })
})
