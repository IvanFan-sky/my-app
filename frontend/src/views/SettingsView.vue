<script setup lang="ts">
/**
 * 系统设置（只读展示）。
 *
 * <p>这一页**刻意全部只读**，而且只管「系统」，不再管「我」：
 * 账号信息与登录令牌已经迁到个人中心（那里才可写）。
 * 两页各管一件事，避免同一个信息在两处出现、改一处忘另一处。
 * 这里展示的两组数据都是**真实取到**的，不放点了没反应的开关。
 */
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchHealth, fetchUserStats } from '@/api/user'
import type { UserStats } from '@/api/user'

const backendOk = ref<boolean | null>(null)
const redisStatus = ref<string>('—')
const stats = ref<UserStats | null>(null)

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
})
</script>

<template>
  <div class="settings-page">
    <PageHeader title="系统设置" description="服务与缓存状态的只读视图（个人资料请到个人中心）" />

    <div class="grid">

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


    </div>

    <p class="footnote">
      本页为系统级状态展示，全部只读。修改个人资料与密码请到「个人中心」。
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
