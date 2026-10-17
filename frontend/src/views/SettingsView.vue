<script setup lang="ts">
/**
 * 系统设置（只读展示）。
 *
 * <p>这一页**刻意全部只读**。可以写的东西（改密码、改邮箱）需要一套
 * 「验证旧密码」的后端接口，属于独立需求；为了把页面填满而放几个点了没反应的
 * 开关，比不放更糟。所以这里只展示四组**真实取到**的信息，并在页面底部写明。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchHealth, fetchUserStats } from '@/api/user'
import type { UserStats } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { decodeJwtPayload, formatRemaining } from '@/utils/jwt'

const auth = useAuthStore()

const backendOk = ref<boolean | null>(null)
const redisStatus = ref<string>('—')
const stats = ref<UserStats | null>(null)

/** 每秒刷新一次，让「剩余有效期」是活的而不是打开页面那一刻的快照 */
const now = ref(Math.floor(Date.now() / 1000))
let ticker: number | undefined

const payload = computed(() => decodeJwtPayload(auth.token))

const issuedAt = computed(() => {
  const iat = payload.value?.iat
  return iat ? new Date(iat * 1000).toLocaleString() : '—'
})

const expiresAt = computed(() => {
  const exp = payload.value?.exp
  return exp ? new Date(exp * 1000).toLocaleString() : '—'
})

const remaining = computed(() => {
  const exp = payload.value?.exp
  if (!exp) return '—'
  return formatRemaining(exp - now.value)
})

/** 剩余时间不足 20 分钟时标黄，提醒该重新登录了 */
const isExpiringSoon = computed(() => {
  const exp = payload.value?.exp
  if (!exp) return false
  return exp - now.value < 20 * 60
})

const redisOk = computed(() => redisStatus.value === 'pong')

const cacheRate = computed(() => {
  if (!stats.value) return '—'
  const { cacheHitCount, cacheMissCount, cacheHitRate } = stats.value
  if (cacheHitCount + cacheMissCount === 0) return '—'
  return `${(cacheHitRate * 100).toFixed(1)}%`
})

async function loadHealth() {
  try {
    const data = await fetchHealth()
    backendOk.value = true
    redisStatus.value = String(data.redis ?? 'unknown')
  } catch {
    backendOk.value = false
    redisStatus.value = '—'
  }
}

async function loadStats() {
  try {
    stats.value = await fetchUserStats()
  } catch {
    stats.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadHealth(), loadStats()])
  ticker = window.setInterval(() => {
    now.value = Math.floor(Date.now() / 1000)
  }, 1000)
})

onUnmounted(() => {
  window.clearInterval(ticker)
})
</script>

<template>
  <div class="settings-page">
    <PageHeader title="系统设置" description="账号、服务与缓存状态的只读视图" />

    <div class="grid">
      <!-- 账号信息 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">账号信息</span>
            <span class="card-icon"><AppIcon name="shield" :size="15" /></span>
          </div>
        </template>

        <dl class="kv">
          <div class="kv-row">
            <dt>用户名</dt>
            <dd>{{ auth.user?.username ?? '—' }}</dd>
          </div>
          <div class="kv-row">
            <dt>用户 ID</dt>
            <dd>{{ auth.user?.id ?? '—' }}</dd>
          </div>
          <div class="kv-row">
            <dt>邮箱</dt>
            <dd :class="{ muted: !auth.user?.email }">{{ auth.user?.email || '未填写' }}</dd>
          </div>
        </dl>
      </el-card>

      <!-- 服务状态 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">服务状态</span>
            <span class="card-icon"><AppIcon name="database" :size="15" /></span>
          </div>
        </template>

        <dl class="kv">
          <div class="kv-row">
            <dt>后端服务</dt>
            <dd>
              <span
                class="pill"
                :class="backendOk === null ? 'is-unknown' : backendOk ? 'is-ok' : 'is-bad'"
              >
                <span class="dot" aria-hidden="true" />
                {{ backendOk === null ? '检测中' : backendOk ? '正常' : '不可用' }}
              </span>
            </dd>
          </div>
          <div class="kv-row">
            <dt>Redis 缓存</dt>
            <dd>
              <span
                class="pill"
                :class="backendOk === null ? 'is-unknown' : redisOk ? 'is-ok' : 'is-bad'"
              >
                <span class="dot" aria-hidden="true" />
                {{ backendOk === null ? '检测中' : redisOk ? '正常' : '不可用' }}
              </span>
            </dd>
          </div>
          <div class="kv-row">
            <dt>数据库</dt>
            <dd class="muted">MySQL 8.4（由后端连接池管理，无独立探测接口）</dd>
          </div>
        </dl>
      </el-card>

      <!-- 缓存统计 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">缓存统计</span>
            <span class="card-icon"><AppIcon name="refresh" :size="15" /></span>
          </div>
        </template>

        <dl class="kv">
          <div class="kv-row">
            <dt>命中率</dt>
            <dd class="strong">{{ cacheRate }}</dd>
          </div>
          <div class="kv-row">
            <dt>命中 / 未命中</dt>
            <dd>{{ stats?.cacheHitCount ?? '—' }} / {{ stats?.cacheMissCount ?? '—' }}</dd>
          </div>
          <div class="kv-row">
            <dt>用户总数</dt>
            <dd>{{ stats?.totalUsers ?? '—' }}</dd>
          </div>
        </dl>

        <p class="note">统计来自后端进程内计数器，重启后端会归零。</p>
      </el-card>

      <!-- 令牌信息 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">登录令牌</span>
            <span class="card-icon"><AppIcon name="clock" :size="15" /></span>
          </div>
        </template>

        <dl class="kv">
          <div class="kv-row">
            <dt>签发时间</dt>
            <dd>{{ issuedAt }}</dd>
          </div>
          <div class="kv-row">
            <dt>到期时间</dt>
            <dd>{{ expiresAt }}</dd>
          </div>
          <div class="kv-row">
            <dt>剩余有效期</dt>
            <dd :class="isExpiringSoon ? 'warn' : 'strong'">{{ remaining }}</dd>
          </div>
        </dl>

        <p class="note">
          令牌在前端只做展示解析（不验签）。真正是否有效由后端判定，无效时接口返回 401
          并自动跳回登录页。
        </p>
      </el-card>
    </div>

    <p class="footnote">
      本页所有内容均为只读展示。修改密码、绑定邮箱等功能需要「验证旧密码」的后端接口，属独立需求。
    </p>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  max-width: var(--app-content-max);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: var(--app-fs-card-title);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--brand-ink);
  background: rgba(15, 118, 110, 0.1);
}

/* ---------------- 键值列表 ---------------- */
.kv {
  margin: 0;
  display: flex;
  flex-direction: column;
}

.kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.kv-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.kv-row dt {
  font-size: var(--app-fs-body);
  color: var(--el-text-color-secondary);
  flex: none;
}

.kv-row dd {
  margin: 0;
  font-size: var(--app-fs-body);
  color: var(--el-text-color-primary);
  text-align: right;
  word-break: break-all;
}

.kv-row dd.strong {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kv-row dd.warn {
  font-weight: 600;
  color: var(--app-status-warn-fg);
}

.muted {
  color: var(--el-text-color-placeholder) !important;
  font-size: var(--app-fs-caption) !important;
}

/* ---------------- 状态胶囊 ---------------- */
.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: var(--app-fs-caption);
  font-weight: 500;
  white-space: nowrap;
}

.pill.is-ok {
  color: var(--app-status-ok-fg);
  background: var(--app-status-ok-bg);
}

.pill.is-bad {
  color: var(--app-status-bad-fg);
  background: var(--app-status-bad-bg);
}

.pill.is-unknown {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* ---------------- 说明文字 ---------------- */
.note {
  margin: 14px 0 0;
  font-size: var(--app-fs-caption);
  line-height: 1.65;
  color: var(--el-text-color-placeholder);
}

.footnote {
  margin: 16px 0 0;
  font-size: var(--app-fs-caption);
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
