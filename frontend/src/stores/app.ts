import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 侧栏折叠状态的持久化 key */
const SIDEBAR_KEY = 'my-app.sidebar-collapsed'

/**
 * 全局状态。
 *
 * 约定：跨页面共享、且不属于某一个页面的状态放这里；
 * 只在单个组件里用的状态留在组件内部的 ref，不要塞进 store。
 */
export const useAppStore = defineStore('app', () => {
  /** 后端是否可达；null 表示还没探测过 */
  const apiReachable = ref<boolean | null>(null)

  /** 后端回报的 Redis 状态 */
  const redis = ref<string>('unknown')

  /**
   * 侧栏是否折叠。持久化到 localStorage：
   * 这是「人的偏好」而不是「本次会话的临时状态」，刷新后保持原样才合理。
   * 读的时候要 try/catch —— localStorage 可能被禁用（隐私模式）。
   */
  const sidebarCollapsed = ref<boolean>(readCollapsed())

  function readCollapsed(): boolean {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === '1'
    } catch {
      return false
    }
  }

  function setApiReachable(value: boolean) {
    apiReachable.value = value
  }

  function setRedis(value: string) {
    redis.value = value
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    try {
      localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed.value ? '1' : '0')
    } catch {
      // 写不进去就算了，功能不受影响，只是刷新后不记得
    }
  }

  return { apiReachable, redis, sidebarCollapsed, setApiReachable, setRedis, toggleSidebar }
})
