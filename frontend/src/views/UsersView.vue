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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { createUser, deleteUser, getUser, pageUsers, updateUser } from '@/api/user'
import type { User } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)

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
  form.email = ''
  form.age = undefined
  // 清掉上一次的校验红字，否则重新打开弹窗会残留
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  isEdit.value = false
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
  rules.value = buildRules(true)
  passwordPlaceholder.value = '留空表示不修改密码'
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

          <span class="count-hint">共 {{ total }} 个用户</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <span class="username-cell">
              <span class="row-avatar" aria-hidden="true">
                {{ row.username.charAt(0).toUpperCase() }}
              </span>
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">
              <AppIcon name="edit" :size="14" />
              <span class="btn-text">编辑</span>
            </el-button>
            <el-tooltip :disabled="!isSelf(row)" content="不能删除当前登录的账号" placement="top">
              <!-- span 包一层：disabled 的按钮不触发鼠标事件，tooltip 会失效 -->
              <span>
                <el-button type="danger" link :disabled="isSelf(row)" @click="remove(row)">
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
