<script setup lang="ts">
/**
 * 用户管理：分页 / 搜索 / 新增 / 编辑 / 删除。
 *
 * <p>与工作台的分工：工作台只做概览（统计 + 最近几条），真正的增删改查全部集中在这里。
 * 两个页面都放一份表格维护成本太高，也容易让用户困惑「到底该在哪操作」。
 *
 * <p>本页不自己探测后端连通性：任何请求失败时 http 层都会弹出可读的错误提示，
 * 再额外放一条「连不上后端」的通栏告警属于重复告知。
 */
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  batchDeleteUsers,
  createUser,
  deleteUser,
  exportUsers,
  getUser,
  pageUsers,
  updateUser,
} from '@/api/user'
import type { User } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)

/** 表格多选。批量删除只对选中的行生效 */
const selectedRows = ref<User[]>([])
const batchDeleting = ref(false)
const exporting = ref(false)

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

const isEdit = ref(false)

const form = reactive<{
  username: string
  password: string
  nickname: string
  email: string
  bio: string
  age?: number
  role: 'ADMIN' | 'USER'
}>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  bio: '',
  age: undefined,
  role: 'USER',
})

/**
 * 把表格行换成 {@link User}。
 *
 * <p>为什么需要它：`el-table` 的行插槽类型是 Element Plus 的 `DefaultRow`
 * （一个不带业务字段的宽泛对象），而 element-plus 目前不支持泛型表格组件，
 * 所以没法让插槽自动推出 `User`。在模板里写 `{ row }: { row: User }` 也不行 ——
 * 插槽属性是逆变检查，实际类型 `{ row: DefaultRow, ... }` 必须能赋给 `{ row: User }`，
 * 而 `DefaultRow` 缺 id/username，直接报错。
 *
 * <p>于是把转换收到这一处：既保留了函数签名的类型安全，又不用在每个调用点写 `as User`。
 * 这个类型不匹配是**启用按需引入后才暴露的** —— 之前 `el-table` 走全局注册，
 * 插槽参数在模板里等于 any，问题被掩盖了。
 */
function asUser(row: unknown): User {
  return row as User
}
/** 编辑自己时禁用角色选择：后端也会拒，这里只是别让人白填 */
const editingSelf = ref(false)

/**
 * 校验规则的差异点：新增时密码必填，编辑时留空表示「不改密码」。
 * 用 computed 让规则跟着模式走，而不是写两套表单。
 */
const rules = ref<FormRules<typeof form>>({})

function buildRules(editMode: boolean): FormRules<typeof form> {
  return {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { max: 64, message: '用户名最长 64 个字符', trigger: 'blur' },
    ],
    password: editMode
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
  }
}

const passwordPlaceholder = ref('必填，至少 6 位')

function resetForm() {
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.email = ''
  form.bio = ''
  form.age = undefined
  form.role = 'USER'
  // 清掉上一次的校验红字，否则重新打开弹窗会残留
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  isEdit.value = false
  editingSelf.value = false
  rules.value = buildRules(false)
  passwordPlaceholder.value = '必填，至少 6 位'
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
  isEdit.value = true
  editingSelf.value = auth.user?.id === row.id
  rules.value = buildRules(true)
  passwordPlaceholder.value = '留空表示不修改密码'
  form.username = row.username
  form.nickname = row.nickname ?? ''
  form.email = row.email ?? ''
  form.bio = row.bio ?? ''
  form.age = row.age ?? undefined
  form.role = row.role === 'ADMIN' ? 'ADMIN' : 'USER'
  form.password = ''
  dialogVisible.value = true
  try {
    const fresh = await getUser(row.id)
    // 期间用户可能已经点了别的行，校验一下再覆盖，避免写错对象
    if (editingId.value === row.id) {
      form.username = fresh.username
      form.nickname = fresh.nickname ?? ''
      form.email = fresh.email ?? ''
      form.bio = fresh.bio ?? ''
      form.age = fresh.age ?? undefined
      form.role = fresh.role === 'ADMIN' ? 'ADMIN' : 'USER'
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
    const payload = {
      username: form.username.trim(),
      nickname: form.nickname.trim() || undefined,
      email: form.email.trim() || undefined,
      bio: form.bio.trim() || undefined,
      age: form.age,
      // 编辑自己时不发 role：后端会拒绝「改自己的角色」，
      // 这里干脆不传，避免每次都吃一个 4000
      role: editingSelf.value ? undefined : form.role,
    }
    if (isEdit.value && editingId.value !== null) {
      await updateUser(editingId.value, {
        ...payload,
        // 留空就不发这个字段，后端据此判断「不改密码」
        password: form.password || undefined,
      })
      ElMessage.success('修改成功')
    } else {
      await createUser({ ...payload, password: form.password })
      ElMessage.success('新增成功')
      query.current = 1
    }
    dialogVisible.value = false
    await loadUsers()
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
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
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
    await loadUsers()
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

function search() {
  query.current = 1
  void loadUsers()
}

function resetSearch() {
  query.keyword = ''
  query.current = 1
  void loadUsers()
}

function onSelectionChange(rows: User[]) {
  selectedRows.value = rows
}

/**
 * 批量删除。
 *
 * <p>二次确认里列出条数而不是用户名列表：选 50 个时列名字会把弹窗撑爆，
 * 而「删除 50 个用户」这个数量信息已经足够让人停下来想一下。
 */
async function batchDelete() {
  const ids = selectedRows.value.map((row) => row.id)
  if (ids.length === 0) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 个用户吗？删除后这些账号的登录令牌会立即失效。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  batchDeleting.value = true
  try {
    const deleted = await batchDeleteUsers(ids)
    // 后端返回的是**实际删除数**：可能有已被删掉的 id，两者不一致时要如实告知
    if (deleted === ids.length) {
      ElMessage.success(`已删除 ${deleted} 个用户`)
    } else {
      ElMessage.warning(`已删除 ${deleted} 个用户（${ids.length - deleted} 个已被删除或不存在）`)
    }
    selectedRows.value = []
    // 删完可能当前页已空，回退一页避免看到空表格
    if (users.value.length === deleted && query.current > 1) {
      query.current -= 1
    }
    await loadUsers()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    batchDeleting.value = false
  }
}

/**
 * 导出当前筛选结果为 CSV。
 *
 * <p>后端直接返回二进制流（带 UTF-8 BOM，Excel 打开中文不乱码），
 * 前端只负责把它落成文件。
 *
 * <p>文件名优先用后端 Content-Disposition 里的那个：它由服务端按
 * `users-yyyyMMdd-HHmmss.csv` 拼好，与 CSV 里的时间列同一个时钟。
 * 只有读不到响应头时才本地拼一个（用 UTC 时间，仅作兜底）。
 */
async function exportCsv() {
  exporting.value = true
  try {
    const { blob, filename } = await exportUsers(query.keyword || undefined)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
    link.href = url
    link.download = filename ?? `users-${stamp}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // 必须释放，否则每导出一次就泄漏一块内存直到页面刷新
    URL.revokeObjectURL(url)
    ElMessage.success('导出已开始')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    exporting.value = false
  }
}

onMounted(loadUsers)
</script>

<template>
  <div class="users-page">
    <PageHeader title="用户管理" description="维护系统账号：新增、编辑、删除与检索">
      <template #actions>
        <el-button :loading="loading" @click="loadUsers">
          <AppIcon name="refresh" :size="15" />
          <span class="btn-text">刷新</span>
        </el-button>
        <el-button :loading="exporting" @click="exportCsv">
          <AppIcon name="download" :size="15" />
          <span class="btn-text">导出 CSV</span>
        </el-button>
        <el-button type="primary" class="app-btn-primary" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          <span class="btn-text">新增用户</span>
        </el-button>
      </template>
    </PageHeader>

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
                <AppIcon name="search" :size="15" />
              </template>
            </el-input>
            <el-button @click="search">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </div>

          <div class="bulk-row">
            <el-button
              type="danger"
              plain
              :disabled="selectedRows.length === 0"
              :loading="batchDeleting"
              @click="batchDelete"
            >
              <AppIcon name="trash" :size="15" />
              <span class="btn-text">
                {{ selectedRows.length > 0 ? `批量删除（${selectedRows.length}）` : '批量删除' }}
              </span>
            </el-button>
            <span class="count-hint">共 {{ total }} 个用户</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="users"
        border
        stripe
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <!-- 多选列：批量删除的数据源。row-key 必须有，否则翻页后选中状态会错乱 -->
        <el-table-column type="selection" width="46" :reserve-selection="false" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <span class="username-cell">
              <span class="row-avatar" aria-hidden="true">
                {{ row.username.charAt(0).toUpperCase() }}
              </span>
              <span class="name-stack">
                <span class="name-main">{{ row.nickname || row.username }}</span>
                <span v-if="row.nickname" class="name-sub">{{ row.username }}</span>
              </span>
              <el-tag v-if="isSelf(asUser(row))" size="small" type="info" effect="plain"
                >当前登录</el-tag
              >
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.role === 'ADMIN' ? 'primary' : 'info'"
              :effect="row.role === 'ADMIN' ? 'light' : 'plain'"
            >
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(asUser(row))">
              <AppIcon name="edit" :size="14" />
              <span class="btn-text">编辑</span>
            </el-button>
            <el-tooltip
              :disabled="!isSelf(asUser(row))"
              content="不能删除当前登录的账号"
              placement="top"
            >
              <!-- span 包一层：disabled 的按钮不触发鼠标事件，tooltip 会失效 -->
              <span>
                <el-button
                  type="danger"
                  link
                  :disabled="isSelf(asUser(row))"
                  @click="remove(asUser(row))"
                >
                  <AppIcon name="trash" :size="14" />
                  <span class="btn-text">删除</span>
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
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submitForm"
      >
        <!--
          用户名在编辑态**必须禁用**。
          后端 UserController.update 只写 nickname/email/bio/age/password，
          从不写 username —— 允许编辑会变成「改了、提示修改成功、刷新后没变」
          的静默失败。真要支持改名是独立需求（唯一索引冲突、令牌里的 sub、
          审计记录都要处理），不是放开一个输入框就能完成的事。
        -->
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            clearable
            :disabled="isEdit"
          />
          <div v-if="isEdit" class="field-hint">用户名是登录标识，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="显示名" prop="nickname">
          <el-input v-model="form.nickname" placeholder="选填，留空则显示用户名" clearable />
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
        <el-form-item label="个人简介" prop="bio">
          <el-input
            v-model="form.bio"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" :disabled="editingSelf" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
          <div v-if="editingSelf" class="field-hint">不能修改自己的角色</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="app-btn-primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.users-page {
  display: flex;
  flex-direction: column;
  max-width: var(--app-content-max);
}

.btn-text {
  margin-left: 6px;
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
  width: 280px;
}

.bulk-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.count-hint {
  font-size: var(--app-fs-caption);
  color: var(--el-text-color-secondary);
}

/* ---------------- 表格 ---------------- */
.username-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 行内首字母头像：让长表格里每行有个视觉锚点，扫读更快 */
.row-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: var(--app-gradient-primary);
  flex: none;
}

.muted {
  color: var(--el-text-color-placeholder);
}

/* 昵称在上、登录名在下：列表中同时能看到「显示成什么」和「登录用什么」 */
.name-stack {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.3;
}

.name-main {
  color: var(--el-text-color-primary);
}

.name-sub {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

/* 字段下方的补充说明，比 placeholder 更明确 */
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .search-input {
    width: 100%;
  }
}
</style>
