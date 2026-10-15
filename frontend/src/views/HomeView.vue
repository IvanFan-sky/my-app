<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createUser,
  deleteUser,
  fetchHealth,
  fetchUserStats,
  getUser,
  pageUsers,
  updateUser,
} from '@/api/user'
import type { User, UserStats } from '@/api/user'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const appStore = useAppStore()
const auth = useAuthStore()

const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)
const stats = ref<UserStats | null>(null)

/** 查询条件与分页状态 */
const query = reactive({ keyword: '', current: 1, size: 10 })

// ---------------------------------------------------------------------------
// 新增 / 编辑弹窗
// ---------------------------------------------------------------------------
// 两者共用同一个弹窗：字段完全一样，只差「密码是否必填」和提交时调哪个接口。
// 分成两个弹窗会让校验规则和样式各写一遍，是设计语言漂移的常见起点。
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const isEdit = computed(() => editingId.value !== null)

const form = reactive<{ username: string; password: string; email: string; age?: number }>({
  username: '',
  password: '',
  email: '',
  age: undefined,
})

/**
 * 校验规则的差异点：新增时密码必填，编辑时留空表示「不改密码」。
 * 用 computed 让规则跟着模式走，而不是写两套表单。
 */
const rules = computed<FormRules<typeof form>>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '用户名最长 64 个字符', trigger: 'blur' },
  ],
  password: isEdit.value
    ? [
        // 编辑态：允许留空；填了就要满足长度要求
        {
          validator: (_rule, value: string, callback: (error?: Error) => void) => {
            if (value && value.length < 6) {
              callback(new Error('密码长度不能少于 6 个字符'))
              return
            }
            callback()
          },
          trigger: 'blur',
        },
      ]
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 72, message: '密码长度需在 6~72 个字符之间', trigger: 'blur' },
      ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}))

/** 密码提示文案随模式变化，避免用户以为编辑时也必须填 */
const passwordPlaceholder = computed(() =>
  isEdit.value ? '留空表示不修改密码' : '必填，至少 6 位',
)

function resetForm() {
  form.username = ''
  form.password = ''
  form.email = ''
  form.age = undefined
  // 清掉上一次的校验红字，否则重新打开弹窗会残留
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 * 点击时先用列表里已有的数据立刻回填（弹窗无延迟出现），
 * 再调 GET /api/users/{id} 拿最新值覆盖 —— 列表可能是几分钟前拉的，
 * 而详情接口走 Redis 缓存，成本很低。这样「立刻响应」和「数据最新」都拿到。
 */
async function openEdit(row: User) {
  editingId.value = row.id
  form.username = row.username
  form.email = row.email ?? ''
  form.age = row.age ?? undefined
  form.password = ''
  dialogVisible.value = true
  try {
    const fresh = await getUser(row.id)
    // 期间用户可能已经点了别的行，校验一下再覆盖，避免写错对象
    if (editingId.value === row.id) {
      form.username = fresh.username
      form.email = fresh.email ?? ''
      form.age = fresh.age ?? undefined
    }
  } catch (error) {
    // 详情拉取失败不影响编辑（列表数据已经回填了），只提示
    ElMessage.warning((error as Error).message)
  }
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value && editingId.value !== null) {
      await updateUser(editingId.value, {
        username: form.username.trim(),
        // 留空就不发这个字段，后端据此判断「不改密码」
        password: form.password || undefined,
        email: form.email.trim() || undefined,
        age: form.age,
      })
      ElMessage.success('修改成功')
    } else {
      await createUser({
        username: form.username.trim(),
        password: form.password,
        email: form.email.trim() || undefined,
        age: form.age,
      })
      ElMessage.success('新增成功')
      query.current = 1
    }
    dialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    submitting.value = false
  }
}

// ---------------------------------------------------------------------------
// 删除
// ---------------------------------------------------------------------------
async function remove(row: User) {
  try {
    // 二次确认：删除是逻辑删除但列表里就看不到了，误点代价不小
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户点了取消，ElMessageBox 会 reject，这里静默返回
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    // 删掉当前页最后一条时，页码要往前退一页，否则会停在空页上
    if (users.value.length === 1 && query.current > 1) {
      query.current -= 1
    }
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

/** 不允许删除自己：删掉后当前令牌立即失效，会直接被踢回登录页，体验很怪 */
function isSelf(row: User) {
  return auth.user?.id === row.id
}

// ---------------------------------------------------------------------------
// 数据加载
// ---------------------------------------------------------------------------
async function loadUsers() {
  loading.value = true
  try {
    const page = await pageUsers({
      keyword: query.keyword.trim() || undefined,
      current: query.current,
      size: query.size,
    })
    users.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await fetchUserStats()
  } catch {
    // 统计只是辅助信息，失败就不显示，不打扰用户
    stats.value = null
  }
}

async function refresh() {
  await Promise.all([loadUsers(), loadStats()])
}

function search() {
  query.current = 1
  void loadUsers()
}

function resetSearch() {
  query.keyword = ''
  query.current = 1
  void loadUsers()
}

/** 缓存命中率显示成百分比；无请求时显示「—」而不是 0%，避免误导 */
const hitRateText = computed(() => {
  if (!stats.value) {
    return '—'
  }
  const { cacheHitCount, cacheMissCount, cacheHitRate } = stats.value
  if (cacheHitCount + cacheMissCount === 0) {
    return '—'
  }
  return `${(cacheHitRate * 100).toFixed(1)}%`
})

onMounted(async () => {
  // 探测后端是否可达，不可达时页面顶部给一条明确提示
  try {
    const data = await fetchHealth()
    appStore.setApiReachable(true)
    appStore.setRedis(String(data.redis ?? 'unknown'))
  } catch {
    appStore.setApiReachable(false)
  }
  await refresh()
})
</script>

<template>
  <div class="user-page">
    <el-alert
      v-if="appStore.apiReachable === false"
      type="error"
      show-icon
      :closable="false"
      title="连不上后端"
      description="请确认后端已在 8080 端口启动：cd backend 然后 mvn spring-boot:run"
    />

    <!-- 概览统计 -->
    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-label">用户总数</span>
        <span class="stat-value">{{ stats?.totalUsers ?? '—' }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">缓存命中</span>
        <span class="stat-value">{{ stats?.cacheHitCount ?? '—' }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">缓存未命中</span>
        <span class="stat-value">{{ stats?.cacheMissCount ?? '—' }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">命中率</span>
        <span class="stat-value">{{ hitRateText }}</span>
      </div>
    </div>

    <el-card class="app-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <div class="search-row">
            <el-input
              v-model="query.keyword"
              placeholder="按用户名或邮箱搜索"
              clearable
              class="search-input"
              @keyup.enter="search"
              @clear="resetSearch"
            >
              <template #prefix>
                <el-icon><svg viewBox="0 0 24 24" width="15" height="15" fill="none"><circle cx="11" cy="11" r="6.2" stroke="currentColor" stroke-width="1.7" /><path d="m15.6 15.6 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg></el-icon>
              </template>
            </el-input>
            <el-button @click="search">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
          </div>

          <el-button type="primary" class="app-btn-primary" @click="openCreate">
            新增用户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="username-cell">
              {{ row.username }}
              <el-tag v-if="isSelf(row)" size="small" type="info" effect="plain">当前登录</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="{ row }">
            <span :class="{ muted: !row.email }">{{ row.email || '未填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="90">
          <template #default="{ row }">
            <span :class="{ muted: row.age === null || row.age === undefined }">
              {{ row.age ?? '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-tooltip
              :disabled="!isSelf(row)"
              content="不能删除当前登录的账号"
              placement="top"
            >
              <!-- span 包一层：disabled 的按钮不触发鼠标事件，tooltip 会失效 -->
              <span>
                <el-button
                  type="danger"
                  link
                  :disabled="isSelf(row)"
                  @click="remove(row)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty :description="query.keyword ? '没有匹配的用户' : '还没有用户'" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="pager"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :total="total"
        @current-change="loadUsers"
        @size-change="search"
      />
    </el-card>

    <!-- 新增 / 编辑共用弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submitForm"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="passwordPlaceholder"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="200" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          class="app-btn-primary"
          :loading="submitting"
          @click="submitForm"
        >
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: var(--app-content-max);
  margin: 0 auto;
}

/* ---------------- 统计卡 ---------------- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: var(--app-radius-inner);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: var(--app-shadow-panel);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
  color: var(--el-text-color-primary);
}

/* ---------------- 列表头 ---------------- */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.username-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-input {
    width: 100%;
  }
}
</style>