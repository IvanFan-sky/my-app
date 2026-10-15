<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AuthLayout from '@/components/AuthLayout.vue'
import FieldIcon from '@/components/FieldIcon.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

/**
 * confirmPassword 只在本地校验，**不会**发给后端。
 * 它的作用是防止用户把密码打错后自己也不知道 —— 这是纯客户端问题，
 * 让后端多收一个字段没有意义。
 */
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
})

// 与后端 RegisterForm 保持一致：用户名必填 ≤64，密码必填 6~72，邮箱选填。
// 注意这里只能按「字符数」校验，后端还会按 UTF-8 字节数判 72 字节上限
// （中文一个字 3 字节）。两者不同源，所以中文长密码可能通过前端、被后端拒 —— 
// 那种情况后端会返回明确的 4000 文案，界面直接展示即可，不必在前端重复实现字节计算。
const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '用户名最长 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度需在 6~72 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      // 自定义校验器：两次输入必须一致
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

/** 已登录用户若直接访问注册页，注册成功后回到原目标页 */
function resolveRedirect(): string {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    // 注册接口成功即返回令牌，store 里会直接写入会话（注册即登录）
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      email: form.email.trim() || undefined,
    })
    ElMessage.success('注册成功，已自动登录')
    await router.replace(resolveRedirect())
  } catch (error) {
    ElMessage.error((error as Error).message)
    // 只清空两个密码框：用户名和邮箱可能只是「被占用」或格式问题，
    // 让用户重打一遍毫无必要
    form.password = ''
    form.confirmPassword = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout title="创建账号" subtitle="填写下面的信息即可开始使用">
    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="submit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="请输入用户名，最长 64 个字符"
          autocomplete="username"
          clearable
          @keyup.enter="submit"
        >
          <template #prefix>
            <FieldIcon name="user" />
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="6~72 个字符"
          autocomplete="new-password"
          show-password
          @keyup.enter="submit"
        >
          <template #prefix>
            <FieldIcon name="lock" />
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入密码"
          autocomplete="new-password"
          show-password
          @keyup.enter="submit"
        >
          <template #prefix>
            <FieldIcon name="lock" />
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="邮箱（选填）" prop="email">
        <el-input
          v-model="form.email"
          placeholder="选填，用于后续找回密码"
          autocomplete="email"
          clearable
          @keyup.enter="submit"
        >
          <template #prefix>
            <FieldIcon name="mail" />
          </template>
        </el-input>
      </el-form-item>

      <el-form-item class="auth-submit-item">
        <el-button type="primary" class="auth-submit" :loading="loading" @click="submit">
          {{ loading ? '注册中…' : '注 册' }}
        </el-button>
      </el-form-item>
    </el-form>

    <template #switch>
      已经有账号了？<RouterLink to="/login">返回登录</RouterLink>
    </template>
  </AuthLayout>
</template>