import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchHealth, fetchUserStats } from '@/api/user'
import type { UserStats } from '@/api/user'

/**
 * 系统状态（后端/Redis 健康 + 用户与缓存统计）。
 *
 * <p><b>为什么要有这个 store</b>：工作台和系统设置两页原来各自发一遍
 * `/api/health` 与 `/api/users/stats`。同一份数据请求两次不只是浪费，
 * 还会让两个页面显示**不一致**的结果（先打开的那页可能已是 30 秒前的快照）。
 * 收到这里之后：同一份数据、同一时刻、一次请求。
 *
 * <p>三个设计取舍：
 * <ul>
 *   <li><b>短 TTL（30 秒）而不是永久缓存</b>：健康状态是会变的，缓存太久等于骗人。
 *       在这个窗口内切页面不会重复请求，超过窗口再进页面会刷新。</li>
 *   <li><b>并发去重</b>：两页在极短时间内都要数据时，共用同一个 Promise，
 *       不会因为"同时打开两个标签页的同一个应用"打两遍。</li>
 *   <li><b>失败不抛给调用方</b>：状态类数据取不到就显示占位符（「—」），
 *       为了一个统计数字弹出一堆红色报错，比不显示更糟。失败时把
 *       {@link lastError} 记下来，需要时可在界面上说明。</li>
 * </ul>
 */
export const useSystemStore = defineStore('system', () => {
  /** 缓存有效期：30 秒内切页面不重复请求 */
  const CACHE_TTL_MS = 30_000

  const backendOk = ref<boolean | null>(null)
  const redisStatus = ref<string>('—')
  const stats = ref<UserStats | null>(null)
  const lastLoadedAt = ref(0)
  const lastError = ref<string | null>(null)
  const loading = ref(false)

  /** 进行中的请求；用来做并发去重 */
  let inflight: Promise<void> | null = null

  const redisOk = computed(() => redisStatus.value === 'pong')

  /** 数据是否是新鲜缓存（界面上可以据此显示"xx 秒前更新"） */
  const isFresh = computed(() => Date.now() - lastLoadedAt.value < CACHE_TTL_MS)

  async function fetchAll() {
    loading.value = true
    lastError.value = null
    try {
      // 两个接口互不依赖，并发发出
      const [health, userStats] = await Promise.all([
        fetchHealth().catch(() => null),
        fetchUserStats().catch(() => null),
      ])

      // 分别处理：健康探测失败不代表统计也失败，反之亦然
      if (health) {
        backendOk.value = true
        redisStatus.value = String(health.redis ?? 'unknown')
      } else {
        backendOk.value = false
        redisStatus.value = '—'
      }

      stats.value = userStats
      lastLoadedAt.value = Date.now()
    } catch (error) {
      // 理论上走不到（上面已经 catch 过），兜底避免未处理的 rejection
      lastError.value = (error as Error).message
    } finally {
      loading.value = false
    }
  }

  /**
   * 取数据。
   *
   * @param force true 表示忽略缓存与在途请求（用户手点"刷新"时用）
   */
  async function load(force = false): Promise<void> {
    if (!force) {
      // 缓存还新鲜：直接用
      if (lastLoadedAt.value > 0 && Date.now() - lastLoadedAt.value < CACHE_TTL_MS) {
        return
      }
      // 已有请求在飞：搭同一班车，不要重复发
      if (inflight) {
        return inflight
      }
    }

    inflight = fetchAll().finally(() => {
      inflight = null
    })
    return inflight
  }

  /** 退出登录时清掉，避免换个账号还看到上一个人的数据快照 */
  function reset() {
    backendOk.value = null
    redisStatus.value = '—'
    stats.value = null
    lastLoadedAt.value = 0
    lastError.value = null
    inflight = null
  }

  return {
    backendOk,
    redisStatus,
    redisOk,
    stats,
    loading,
    lastLoadedAt,
    lastError,
    isFresh,
    load,
    reset,
  }
})
