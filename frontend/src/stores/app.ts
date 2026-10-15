import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全局状态示例（Pinia 的 setup 写法）。
 *
 * 约定：跨页面共享、且不属于某一个页面的状态放这里；
 * 只在单个组件里用的状态留在组件内部的 ref，不要塞进 store。
 */
export const useAppStore = defineStore('app', () => {
  /** 后端是否可达；null 表示还没探测过 */
  const apiReachable = ref<boolean | null>(null)

  /** 后端回报的 Redis 状态 */
  const redis = ref<string>('unknown')

  function setApiReachable(value: boolean) {
    apiReachable.value = value
  }

  function setRedis(value: string) {
    redis.value = value
  }

  return { apiReachable, redis, setApiReachable, setRedis }
})
