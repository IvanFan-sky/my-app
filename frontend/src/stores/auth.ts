import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ROLE_ADMIN, fetchMe, login as loginApi, register as registerApi } from '@/api/auth'
import type { AuthUser, RegisterPayload } from '@/api/auth'

const TOKEN_KEY = 'my-app.token'
const USER_KEY = 'my-app.user'

/**
 * 把用户信息从 localStorage 读回来。
 *
 * <p>localStorage 的内容是用户可改的，所以这里必须 try/catch：
 * 手工写坏 JSON 或塞个字符串进来，不该让整个应用白屏。
 * 注意这只影响「界面先显示什么」，真正的权限判定在后端 ——
 * 前端存的 user 只是用来避免刷新时头像/用户名闪一下。
 */
function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

/**
 * 登录态（Pinia setup 写法）。
 *
 * <p>token 存 localStorage 而不是内存，是为了刷新页面不掉登录。
 * 代价：一旦有 XSS，token 就能被读走。彻底的做法是改成后端下发的
 * HttpOnly Cookie（前端读不到，XSS 偷不走），但那需要后端支持 Cookie 与 CSRF 防护，
 * 是独立任务。当前方案是开发期的常见取舍，不是「安全上没想过」。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(readStoredUser())

  /** 是否已持有令牌（不代表后端一定还认它，见 restore()） */
  const isAuthenticated = computed(() => token.value.length > 0)

  /**
   * 是否管理员。
   *
   * <p>**只用于界面控制**（隐藏菜单、路由拦截），不是安全边界 ——
   * 用户可以改 localStorage 把自己伪装成管理员，但后端
   * AdminOnlyInterceptor 会用令牌里的角色再判一次，伪造无效。
   * 前端做这一层只是为了不让普通用户点进必然 403 的页面。
   */
  const isAdmin = computed(() => user.value?.role === ROLE_ADMIN)

  /** 头像/头部展示用的名称：优先显示名，没设就回退用户名 */
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  function setSession(newToken: string, newUser: AuthUser) {
    token.value = newToken
    user.value = newUser
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  /**
   * 用后端返回的最新用户信息刷新本地会话（令牌不变）。
   *
   * <p>改完昵称后必须调它，否则头部还显示旧名字 ——
   * 页面局部刷新了、全局状态没变，看起来就像「没保存成功」。
   */
  function setUser(newUser: AuthUser) {
    user.value = newUser
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  /**
   * 清空登录态。
   *
   * <p>只清本地。后端签的是无状态 JWT，服务端没有会话可销；
   * 所以「退出登录」的真实含义是「客户端不再持有令牌」，
   * 在令牌过期前，被复制走的那个令牌仍然有效。要做真正的即时失效，
   * 需要服务端黑名单（Redis），是独立任务。
   */
  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  /** 登录：成功后写入会话 */
  async function login(username: string, password: string) {
    const result = await loginApi({ username, password })
    setSession(result.token, result.user)
    return result
  }

  /**
   * 注册：后端注册成功即签发令牌，所以这里和 login 一样直接写入会话，
   * 用户不需要「注册完再登一次」。
   */
  async function register(payload: RegisterPayload) {
    const result = await registerApi(payload)
    setSession(result.token, result.user)
    return result
  }

  function logout() {
    clearSession()
  }

  /**
   * 用本地 token 向后端确认登录态，并刷新用户信息。
   *
   * <p>为什么必须真调一次接口：本地有 token 只说明「曾经登录过」。
   * 用户可能已被删除，或 token 已过期，只有后端说了算。
   *
   * @returns 登录态是否有效
   */
  async function restore(): Promise<boolean> {
    if (!token.value) {
      return false
    }
    try {
      user.value = await fetchMe()
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
      return true
    } catch {
      // 令牌过期 / 无效 / 用户已删除，一律当作未登录处理
      clearSession()
      return false
    }
  }

  return { token, user, isAuthenticated, isAdmin, displayName, setUser, login, register, logout, restore }
})
