<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createUser, deleteUser, fetchHealth, pageUsers } from '@/api/user'
import type { User } from '@/api/user'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)

// 查询条件与分页状态
const query = reactive({ username: '', current: 1, size: 10 })

// 新增表单
const form = reactive<{ username: string; email: string; age?: number }>({
  username: '',
  email: '',
  age: undefined,
})

/** 探测后端与 Redis：把「服务是否通了」直接显示在页面上，省得靠猜 */
async function loadHealth() {
  try {
    const data = await fetchHealth()
    appStore.setApiReachable(true)
    appStore.setRedis(String(data.redis ?? 'unknown'))
  } catch {
    appStore.setApiReachable(false)
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const page = await pageUsers({
      username: query.username || undefined,
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

async function submit() {
  if (!form.username.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  try {
    await createUser({
      username: form.username.trim(),
      email: form.email.trim() || undefined,
      age: form.age,
    })
    ElMessage.success('新增成功')
    form.username = ''
    form.email = ''
    form.age = undefined
    query.current = 1
    await loadUsers()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function remove(id: number) {
  try {
    await deleteUser(id)
    ElMessage.success('删除成功')
    await loadUsers()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function search() {
  query.current = 1
  void loadUsers()
}

onMounted(async () => {
  await loadHealth()
  await loadUsers()
})
</script>

<template>
  <div class="home">
    <el-alert
      v-if="appStore.apiReachable === false"
      type="error"
      show-icon
      :closable="false"
      title="连不上后端"
      description="请确认后端已在 8080 端口启动：cd backend 然后 mvn spring-boot:run"
    />

    <el-card v-else shadow="never" class="status-card">
      <template #header>连通性</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="后端">已连通</el-descriptions-item>
        <el-descriptions-item label="Redis">{{ appStore.redis }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="form-card">
      <template #header>新增用户</template>
      <el-form :inline="true" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="必填" style="width: 160px" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" style="width: 200px" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="form.age" :min="0" :max="200" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">提交</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="table-header">
          <span>用户列表</span>
          <div class="table-header-actions">
            <el-input
              v-model="query.username"
              placeholder="按用户名模糊搜索"
              clearable
              style="width: 200px"
              @keyup.enter="search"
            />
            <el-button @click="search">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="age" label="年龄" width="90" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
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
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-card,
.form-card {
  max-width: 900px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
