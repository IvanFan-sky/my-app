<script setup lang="ts">
/**
 * 工作台（首页概览）。
 *
 * <p>这一页只放**能真实取到**的数据。刻意没有趋势折线图和「环比 +12%」：
 * 后端没有时序聚合接口，画出来就是假数据，比不画更糟。
 *
 * <p>数据来源：
 * <ul>
 *   <li>用户总数 / 缓存统计 —— GET /api/users/stats</li>
 *   <li>最近用户 —— GET /api/users?current=1&size=5</li>
 *   <li>后端与 Redis 状态 —— GET /api/health（白名单，无需登录）</li>
 * </ul>
 */
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatCard from '@/components/layout/StatCard.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchHealth, fetchUserStats, pageUsers } from '@/api/user'
import type { User, UserStats } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const stats = ref<UserStats | null>(null)
const recent = ref<User[]>([])
const loadingRecent = ref(false)
/** 后端与 Redis 状态；null 表示尚未探测 */
const backendOk = ref<boolean | null>(null)
const redisStatus = ref<string>('—')
const redisOk = computed(() => redisStatus.value === 'pong')

/** 按当前时间给出问候语，比固定「欢迎」更有生活感 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/** 缓存命中率：没有请求过时显示「—」，不显示 0% */
const hitRate = computed(() => {
  if (!stats.value) return null
  const { cacheHitCount, cacheMissCount, cacheHitRate } = stats.value
  if (cacheHitCount + cacheMissCount === 0) return null
  return `${(cacheHitRate * 100).toFixed(1)}%`
})

/** 时间展示：只取到分钟，列表里不需要秒 */
function shortTime(v?: string) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

async function loadStats() {
  try {
    stats.value = await fetchUserStats()
  } catch {
    // 概览类数据失败不打扰用户，卡片会显示占位符
    stats.value = null
  }
}

async function loadRecent() {
  loadingRecent.value = true
  try {
    const page = await pageUsers({ current: 1, size: 5 })
    recent.value = page.records
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loadingRecent.value = false
  }
}

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

async function refreshAll() {
  // 三个请求互不依赖，并发发出，省一半等待时间
  await Promise.all([loadStats(), loadRecent(), loadHealth()])
}

onMounted(refreshAll)
</script>

<template>
  <div class="dashboard">
    <PageHeader title="工作台" description="一眼看到当前系统状态与最近变化">
      <template #actions>
        <el-button :loading="loadingRecent" @click="refreshAll">
          <AppIcon name="refresh" :size="15" />
          <span class="btn-text">刷新</span>
        </el-button>
        <RouterLink v-slot="{ navigate }" :to="{ name: 'users' }" custom>
          <el-button type="primary" class="app-btn-primary" @click="navigate">
            <AppIcon name="users" :size="15" />
            <span class="btn-text">用户管理</span>
          </el-button>
        </RouterLink>
      </template>
    </PageHeader>

    <!-- 欢迎条 -->
    <section class="welcome">
      <div class="welcome-text">
        <h2 class="welcome-title">{{ greeting }}，{{ auth.user?.username ?? '朋友' }}</h2>
        <p class="welcome-sub">
          当前登录账号 ID 为 {{ auth.user?.id ?? '—' }}。左侧可进入用户管理与系统设置。
        </p>
      </div>
      <span class="welcome-badge">
        <AppIcon name="shield" :size="14" />
        <span>已通过 JWT 鉴权</span>
      </span>
    </section>

    <!-- 统计卡 -->
    <div class="stat-grid">
      <StatCard
        label="用户总数"
        :value="stats?.totalUsers"
        icon="users"
        tone="teal"
        hint="不含已逻辑删除"
      />
      <StatCard
        label="缓存命中率"
        :value="hitRate"
        icon="database"
        tone="sky"
        hint="Redis 用户缓存"
      />
      <StatCard
        label="缓存命中次数"
        :value="stats?.cacheHitCount"
        icon="shield"
        tone="violet"
        hint="自后端启动累计"
      />
      <StatCard
        label="缓存未命中"
        :value="stats?.cacheMissCount"
        icon="clock"
        tone="amber"
        hint="未命中会回源数据库"
      />
    </div>

    <div class="cols">
      <!-- 最近注册的用户 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">最近用户</span>
            <RouterLink :to="{ name: 'users' }" class="app-link more">
              查看全部
              <AppIcon name="arrow-right" :size="13" />
            </RouterLink>
          </div>
        </template>

        <div v-loading="loadingRecent" class="recent">
          <div v-if="recent.length === 0 && !loadingRecent" class="empty">
            <el-empty description="还没有用户" :image-size="70" />
          </div>
          <ul v-else class="recent-list">
            <li v-for="u in recent" :key="u.id" class="recent-item">
              <span class="recent-avatar" aria-hidden="true">
                {{ u.username.charAt(0).toUpperCase() }}
              </span>
              <span class="recent-main">
                <span class="recent-name">{{ u.username }}</span>
                <span class="recent-mail">{{ u.email || '未填写邮箱' }}</span>
              </span>
              <span class="recent-time">{{ shortTime(u.createdAt) }}</span>
            </li>
          </ul>
        </div>
      </el-card>

      <!-- 服务状态 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">服务状态</span>
            <RouterLink :to="{ name: 'settings' }" class="app-link more">
              详情
              <AppIcon name="arrow-right" :size="13" />
            </RouterLink>
          </div>
        </template>

        <ul class="status-list">
          <li class="status-item">
            <span class="status-name">后端服务</span>
            <span
              class="status-pill"
              :class="backendOk === null ? 'is-unknown' : backendOk ? 'is-ok' : 'is-bad'"
            >
              <span class="status-dot" aria-hidden="true" />
              {{ backendOk === null ? '检测中' : backendOk ? '正常' : '不可用' }}
            </span>
          </li>
          <li class="status-item">
            <span class="status-name">Redis 缓存</span>
            <span
              class="status-pill"
              :class="backendOk === null ? 'is-unknown' : redisOk ? 'is-ok' : 'is-bad'"
            >
              <span class="status-dot" aria-hidden="true" />
              {{ backendOk === null ? '检测中' : redisOk ? '正常' : '不可用' }}
            </span>
          </li>
          <li class="status-item">
            <span class="status-name">登录方式</span>
            <span class="status-plain">JWT（Bearer）</span>
          </li>
        </ul>

        <p class="status-note">Redis 不可用时系统仍可正常工作，缓存会自动降级为直接查库。</p>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: var(--app-content-max);
}

.btn-text {
  margin-left: 6px;
}

/* ---------------- 欢迎条 ---------------- */
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 20px 22px;
  border-radius: var(--app-radius-inner);
  /* 用品牌渐变做主视觉锚点，但**文字是白字**，所以底色用深端渐变（对比度达标） */
  background: var(--app-gradient-primary);
  color: #fff;
  box-shadow: var(--app-shadow-panel);
}

.welcome-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.welcome-sub {
  margin: 6px 0 0;
  font-size: var(--app-fs-secondary);
  /* 0.92 而不是 0.8：在渐变上 0.8 白大约只有 3.3:1，正文读起来发虚 */
  color: rgba(255, 255, 255, 0.92);
}

.welcome-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: var(--app-fs-caption);
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.24);
  white-space: nowrap;
}

/* ---------------- 统计卡栅格 ---------------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

/* ---------------- 两列区 ---------------- */
.cols {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
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

.more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--app-fs-caption);
  font-weight: 500;
}

/* ---------------- 最近用户 ---------------- */
.recent {
  min-height: 80px;
}

.recent-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.recent-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.recent-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--app-gradient-primary);
  flex: none;
}

.recent-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recent-name {
  font-size: var(--app-fs-body);
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.recent-mail {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  margin-left: auto;
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.empty {
  padding: 4px 0;
}

/* ---------------- 服务状态 ---------------- */
.status-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.status-item:last-child {
  border-bottom: none;
}

.status-name {
  font-size: var(--app-fs-body);
  color: var(--el-text-color-regular);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: var(--app-fs-caption);
  font-weight: 500;
  white-space: nowrap;
}

/* 状态色：不只用颜色区分，前面还有一个小圆点，色盲用户也能分辨 */
.status-pill.is-ok {
  color: var(--app-status-ok-fg);
  background: var(--app-status-ok-bg);
}

.status-pill.is-bad {
  color: var(--app-status-bad-fg);
  background: var(--app-status-bad-bg);
}

.status-pill.is-unknown {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-plain {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-secondary);
}

.status-note {
  margin: 12px 0 0;
  font-size: var(--app-fs-caption);
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .cols {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .welcome {
    padding: 16px;
  }
}
</style>
