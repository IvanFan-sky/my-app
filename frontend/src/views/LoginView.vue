<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

// 规则与后端 LoginForm 的校验保持一致：用户名必填且有长度上限，密码必填。
// 前端校验只为「少发一次必然失败的请求」，后端仍是唯一权威。
const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '用户名最长 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 72, message: '密码最长 72 个字符', trigger: 'blur' },
  ],
}

/** 登录成功后回跳到守卫记下的原目标页；没有就回首页 */
function resolveRedirect(): string {
  const redirect = route.query.redirect
  // query 的值类型是 string | string[] | null，必须判类型再断言，
  // 否则「以数组为路径」会在 vue-router 里抛错
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}

async function submit() {
  if (!formRef.value) {
    return
  }
  // validate 在校验失败时 reject，这里必须捕获，否则控制台会报未处理的 Promise
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    await router.replace(resolveRedirect())
  } catch (error) {
    // http.ts 已经把后端 code != 0 转成了 rejected Promise 带可读 message，
    // 这里不需要再判断 code
    ElMessage.error((error as Error).message)
    form.password = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <span class="login-title">my-app</span>
          <span class="login-subtitle">请登录后使用</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 开发期提示：种子账号写在这里，省得翻 sql/init.sql。
           部署前应删掉这段，否则等于把账号信息挂在登录页上。 -->
      <el-alert type="info" :closable="false" class="hint">
        <template #title>
          测试账号：alice / alice123、bob / bob123、carol / carol123
          （见 sql/init.sql，上线前请删除本提示）
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--el-fill-color-light);
}

.login-card {
  width: 380px;
}

.login-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
}

.login-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.submit-btn {
  width: 100%;
}

.hint {
  margin-top: 4px;
}
</style>
