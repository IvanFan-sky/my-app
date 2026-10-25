import { AxiosError } from 'axios'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'

/**
 * http.ts 的拦截器行为。
 *
 * <p><b>为什么这些必须测</b>：续期逻辑是全项目最容易「改对了功能、改坏了时序」
 * 的地方。一个页面同时发 5 个请求、access 恰好过期时，如果每个请求各自去续期，
 * 就会有 5 次刷新、5 组新令牌，最后只有最后一组有效 —— 用户看到的是
 * 莫名其妙的「登录已失效」。这类问题不会在单请求的手测里出现。
 *
 * <p>测试方式：替换 axios 的 adapter，不碰真实网络。module 级别的
 * `refreshing` / `retried` 是跨请求共享的，所以每个用例都 `resetModules`
 * 重新加载模块，避免用例之间互相污染。
 */

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  const response = {
    data: null,
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  } as unknown as AxiosResponse
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, response)
}

function ok(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  } as unknown as AxiosResponse
}

/** 重新加载 http 模块，拿到干净的模块级状态 */
async function loadHttpModule() {
  vi.resetModules()
  return import('@/api/http')
}

describe('http 拦截器', () => {
  it('并发 5 个请求同时 401 时，只续期一次，且全部重发成功', async () => {
    const mod = await loadHttpModule()
    const http = mod.default

    let calls = 0
    // 前 5 次（第一轮）全部 401，之后的（重发）成功
    const adapter: AxiosAdapter = async (config) => {
      calls += 1
      if (calls <= 5) {
        throw unauthorized(config)
      }
      return ok(config, { code: 0, message: 'ok', data: { ok: true } })
    }
    http.defaults.adapter = adapter

    let refreshCalls = 0
    mod.setRefreshHandler(async () => {
      refreshCalls += 1
      // 加一点延迟，模拟真实网络：没有它的话单飞与否都可能碰巧只调一次
      await new Promise((resolve) => setTimeout(resolve, 20))
      return true
    })
    let unauthorizedCalls = 0
    mod.setUnauthorizedHandler(() => {
      unauthorizedCalls += 1
    })

    const results = await Promise.all(
      Array.from({ length: 5 }, () => mod.request<{ ok: boolean }>({ url: '/api/whatever' })),
    )

    expect(refreshCalls).toBe(1)
    expect(unauthorizedCalls).toBe(0)
    expect(results).toHaveLength(5)
    expect(results.every((r) => r.ok)).toBe(true)
  })

  it('续期失败时按未登录处理，不会无限重试', async () => {
    const mod = await loadHttpModule()
    const http = mod.default

    let adapterCalls = 0
    const adapter: AxiosAdapter = async (config) => {
      adapterCalls += 1
      throw unauthorized(config)
    }
    http.defaults.adapter = adapter

    let refreshCalls = 0
    mod.setRefreshHandler(async () => {
      refreshCalls += 1
      return false
    })
    let unauthorizedCalls = 0
    mod.setUnauthorizedHandler(() => {
      unauthorizedCalls += 1
    })

    const results = await Promise.allSettled(
      Array.from({ length: 3 }, () => mod.request({ url: '/api/whatever' })),
    )

    // 仍然是单飞：3 个请求共用一次失败的续期
    expect(refreshCalls).toBe(1)
    expect(unauthorizedCalls).toBe(3)
    expect(results.every((r) => r.status === 'rejected')).toBe(true)
    // 关键：不能因为重发而死循环 —— 3 个请求各打 1 次 adapter，没有第二次重发
    expect(adapterCalls).toBe(3)
  })

  it('请求会带上令牌；业务码非 0 时转成 rejected Promise', async () => {
    const mod = await loadHttpModule()
    const http = mod.default

    let seenAuth: string | undefined
    const adapter: AxiosAdapter = async (config) => {
      seenAuth = String(config.headers.Authorization ?? '')
      return ok(config, { code: 4009, message: '用户名已被占用', data: null })
    }
    http.defaults.adapter = adapter
    mod.setTokenGetter(() => 'token-abc')

    await expect(mod.request({ url: '/api/users', method: 'post' })).rejects.toThrow(
      '用户名已被占用',
    )
    expect(seenAuth).toBe('Bearer token-abc')
  })
})
