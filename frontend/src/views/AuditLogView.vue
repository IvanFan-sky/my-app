<script setup lang="ts">
/**
 * 审计日志（管理员可见）。
 *
 * <p>这一页回答的是「谁在什么时候对谁做了什么」。三个刻意的设计：
 * <ul>
 *   <li><b>动作标签由后端给</b>（`actionLabel`）：前端不维护一份动作名映射，
 *       否则后端加了新动作、前端就会显示成 `BATCH_DELETE` 这种原始枚举名。</li>
 *   <li><b>失败记录也显示</b>，并且用红色标出来：连续登录失败本身就是重要信号，
 *       只显示成功等于把最该看的东西藏起来。</li>
 *   <li><b>detail 原样展示</b>而不是解析成好看的字段：它是自由结构的 JSON，
 *       每个动作的字段都不同，强行解析反而可能丢信息。</li>
 * </ul>
 */
import { onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchAuditActions, pageAuditLogs } from '@/api/audit'
import type { AuditLog } from '@/api/audit'

const logs = ref<AuditLog[]>([])
const total = ref(0)
const loading = ref(false)
const actionOptions = ref<Array<{ value: string; label: string }>>([])

const query = reactive({
  keyword: '',
  action: '',
  current: 1,
  size: 20,
})

async function load() {
  loading.value = true
  try {
    const page = await pageAuditLogs(query)
    logs.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 搜索：回到第一页，否则可能停在一个超出总页数的页码上看到空白 */
function search() {
  query.current = 1
  void load()
}

function resetFilters() {
  query.keyword = ''
  query.action = ''
  query.current = 1
  void load()
}

function onPageChange(page: number) {
  query.current = page
  void load()
}

function onSizeChange(size: number) {
  query.size = size
  query.current = 1
  void load()
}

/** 时间展示到秒：审计场景里「差几秒」往往是有意义的 */
function formatTime(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(async () => {
  // 动作选项失败不影响列表本身，静默降级成「全部」也能用
  try {
    actionOptions.value = await fetchAuditActions()
  } catch {
    actionOptions.value = []
  }
  await load()
})
</script>

<template>
  <div class="audit-page">
    <PageHeader title="审计日志" description="写操作与登录记录，只增不改">
      <template #actions>
        <el-button :loading="loading" @click="load">
          <AppIcon name="refresh" :size="15" />
          <span class="btn-text">刷新</span>
        </el-button>
      </template>
    </PageHeader>

    <el-card class="app-panel" shadow="never">
      <template #header>
        <div class="toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="按操作人或对象用户名搜索（前缀）"
            clearable
            class="toolbar-search"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix>
              <AppIcon name="search" :size="15" />
            </template>
          </el-input>

          <el-select
            v-model="query.action"
            placeholder="全部动作"
            clearable
            class="toolbar-select"
            @change="search"
          >
            <el-option
              v-for="option in actionOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <el-button class="app-btn-primary" type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>

          <span class="count-hint">共 {{ total }} 条</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="logs" border stripe>
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="actionLabel" label="动作" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'primary' : 'danger'" effect="light">
              {{ row.actionLabel || row.action }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作人" min-width="140">
          <template #default="{ row }">
            <span v-if="row.actorUsername">{{ row.actorUsername }}</span>
            <span v-else class="muted">（未知/未登录）</span>
          </template>
        </el-table-column>

        <el-table-column label="对象" min-width="140">
          <template #default="{ row }">
            <span v-if="row.targetUsername">{{ row.targetUsername }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <span :class="row.success ? 'ok' : 'bad'">{{ row.success ? '成功' : '失败' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="ip" label="来源 IP" width="130">
          <template #default="{ row }">
            <span class="mono">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="detail" label="补充信息" min-width="220">
          <template #default="{ row }">
            <span class="detail">{{ row.detail || '—' }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            :description="query.keyword || query.action ? '没有匹配的日志' : '还没有日志'"
          />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="query.current"
        :page-size="query.size"
        :page-sizes="[20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <p class="footnote">
      只记录写操作与登录，不记录查询。日志只增不改、没有删除入口 —— 能被修改的日志就失去了审计价值。
    </p>
  </div>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: var(--app-content-max);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: 260px;
}

.toolbar-select {
  width: 150px;
}

.count-hint {
  margin-left: auto;
  font-size: var(--app-fs-secondary);
  color: var(--el-text-color-secondary);
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.btn-text {
  margin-left: 6px;
}

.mono {
  font-variant-numeric: tabular-nums;
  font-size: var(--app-fs-secondary);
}

.muted {
  color: var(--el-text-color-placeholder);
}

.ok {
  color: var(--app-status-ok-fg);
  font-weight: 600;
}

/* 失败用状态色，不裸写红值 —— 与工作台/系统设置保持一致 */
.bad {
  color: var(--app-status-bad-fg);
  font-weight: 600;
}

.detail {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.footnote {
  margin: 0;
  font-size: var(--app-fs-caption);
  line-height: 1.7;
  color: var(--el-text-color-placeholder);
}
</style>
