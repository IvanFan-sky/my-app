<script setup lang="ts">
/**
 * 个人中心：看自己的账号，改自己的资料与密码。
 *
 * <p>三个设计要点：
 * <ol>
 *   <li><b>所有写操作都不带 id</b> —— 后端身份只从令牌取，
 *       所以这里不存在「改错人」的可能，前端也不需要做任何归属校验。</li>
 *   <li><b>资料表单里没有角色/用户名</b>：角色提权必须堵死，用户名是登录标识。
 *       这与后端 ProfileForm 的字段白名单一致。</li>
 *   <li><b>改密码要验原密码</b>，且原密码错时**不会被登出**
 *       （后端返 4000 而非 4010，http 层不会触发清登录态）。</li>
 * </ol>
 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { changePassword, updateProfile } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { decodeJwtPayload, formatRemaining } from '@/utils/jwt'

const auth = useAuthStore()

// ---------------------------------------------------------------------------
// 资料表单
// ---------------------------------------------------------------------------
const profileRef = ref<FormInstance>()
const savingProfile = ref(false)

const profile = reactive<{ nickname: string; email: string; bio: string; age?: number }>({
  nickname: '',
  email: '',
  bio: '',
  age: undefined,
})

/** 用当前会话填充表单。会话变了（登录/刷新恢复）要重新填，故用 watch */
function fillFromUser() {
  profile.nickname = auth.user?.nickname ?? ''
  profile.email = auth.user?.email ?? ''
  profile.bio = auth.user?.bio ?? ''
  profile.age = auth.user?.age ?? undefined
}
watch(() => auth.user, fillFromUser, { immediate: true })

const profileRules: FormRules<typeof profile> = {
  nickname: [{ max: 64, message: '显示名最长 64 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  bio: [{ max: 255, message: '个人简介最长 255 个字符', trigger: 'blur' }],
}

async function saveProfile() {
  if (!profileRef.value) return
  const valid = await profileRef.value.validate().catch(() => false)
  if (!valid) return

  savingProfile.value = true
  try {
    const fresh = await updateProfile({
      // 留空就不发，后端据此写 null（「没填」与「填了空串」语义不同）
      nickname: profile.nickname.trim() || undefined,
      email: profile.email.trim() || undefined,
      bio: profile.bio.trim() || undefined,
      age: profile.age,
    })
    // 关键：同步全局会话，否则顶栏还显示旧昵称，看起来像没保存成功
    auth.setUser(fresh)
    fillFromUser()
    ElMessage.success('资料已保存')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingProfile.value = false
  }
}

// ---------------------------------------------------------------------------
// 修改密码
// ---------------------------------------------------------------------------
const pwdRef = ref<FormInstance>()
const savingPwd = ref(false)

const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules<typeof pwd> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 72, message: '新密码长度需在 6~72 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== pwd.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function savePassword() {
  if (!pwdRef.value) return
  const valid = await pwdRef.value.validate().catch(() => false)
  if (!valid) return

  savingPwd.value = true
  try {
    // 改密会让该用户**所有**旧令牌立即作废（token_version +1），
    // 后端顺手返回一对新令牌；不套用的话当前这端也会被踢回登录页。
    const result = await changePassword({
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
    })
    auth.applyLoginResult(result)
    ElMessage.success('密码已修改，其他设备需要重新登录')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirmPassword = ''
    pwdRef.value.clearValidate()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingPwd.value = false
  }
}

// ---------------------------------------------------------------------------
// 令牌信息（只读展示）
// ---------------------------------------------------------------------------
const now = ref(Math.floor(Date.now() / 1000))
let ticker: number | undefined

const payload = computed(() => decodeJwtPayload(auth.token))
const issuedAt = computed(() =>
  payload.value?.iat ? new Date(payload.value.iat * 1000).toLocaleString() : '—',
)
const expiresAt = computed(() =>
  payload.value?.exp ? new Date(payload.value.exp * 1000).toLocaleString() : '—',
)
const remaining = computed(() =>
  payload.value?.exp ? formatRemaining(payload.value.exp - now.value) : '—',
)
/** 剩余不足 20 分钟时标黄，提醒该重新登录 */
const expiringSoon = computed(() =>
  payload.value?.exp ? payload.value.exp - now.value < 20 * 60 : false,
)

/** 注册时间：只取日期，个人中心不需要精确到分 */
const joinedAt = computed(() => {
  const v = auth.user?.createdAt
  return v ? v.replace('T', ' ').slice(0, 10) : '—'
})

const initial = computed(() => (auth.displayName || '?').charAt(0).toUpperCase())

onMounted(() => {
  ticker = window.setInterval(() => {
    now.value = Math.floor(Date.now() / 1000)
  }, 1000)
})
onUnmounted(() => window.clearInterval(ticker))
</script>

<template>
  <div class="profile-page">
    <PageHeader title="个人中心" description="查看并修改你自己的账号信息" />

    <!-- 账号概览 -->
    <section class="hero">
      <span class="hero-avatar" aria-hidden="true">{{ initial }}</span>
      <div class="hero-main">
        <div class="hero-name">
          {{ auth.displayName || '—' }}
          <span v-if="auth.isAdmin" class="hero-role">管理员</span>
          <span v-else class="hero-role is-user">普通用户</span>
        </div>
        <div class="hero-sub">
          登录名 <strong>{{ auth.user?.username ?? '—' }}</strong>
          <span class="dot" aria-hidden="true">·</span>
          ID {{ auth.user?.id ?? '—' }}
          <span class="dot" aria-hidden="true">·</span>
          注册于 {{ joinedAt }}
        </div>
      </div>
    </section>

    <div class="grid">
      <!-- 基础资料 -->
      <el-card class="app-panel" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">基础资料</span>
            <span class="card-icon"><AppIcon name="user" :size="15" /></span>
          </div>
        </template>

        <el-form
          ref="profileRef"
          :model="profile"
          :rules="profileRules"
          label-position="top"
          @submit.prevent="saveProfile"
        >
          <el-form-item label="显示名" prop="nickname">
            <el-input
              v-model="profile.nickname"
              placeholder="选填，用于界面展示；留空则显示登录名"
              clearable
            />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profile.email" placeholder="选填" clearable />
          </el-form-item>
          <el-form-item label="年龄" prop="age">
            <el-input-number v-model="profile.age" :min="0" :max="200" />
          </el-form-item>
          <el-form-item label="个人简介" prop="bio">
            <el-input
              v-model="profile.bio"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="选填，最多 255 个字符"
            />
          </el-form-item>
          <el-form-item class="submit-item">
            <el-button
              type="primary"
              class="app-btn-primary"
              :loading="savingProfile"
              @click="saveProfile"
            >
              保存资料
            </el-button>
          </el-form-item>
        </el-form>

        <p class="note">登录名与角色不在这里修改：前者是登录标识，后者需要管理员操作。</p>
      </el-card>

      <div class="col">
        <!-- 修改密码 -->
        <el-card class="app-panel" shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">修改密码</span>
              <span class="card-icon"><AppIcon name="shield" :size="15" /></span>
            </div>
          </template>

          <el-form
            ref="pwdRef"
            :model="pwd"
            :rules="pwdRules"
            label-position="top"
            @submit.prevent="savePassword"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwd.oldPassword"
                type="password"
                autocomplete="current-password"
                show-password
                placeholder="请输入当前密码"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwd.newPassword"
                type="password"
                autocomplete="new-password"
                show-password
                placeholder="6~72 个字符"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwd.confirmPassword"
                type="password"
                autocomplete="new-password"
                show-password
                placeholder="请再次输入新密码"
              />
            </el-form-item>
            <el-form-item class="submit-item">
              <el-button
                type="primary"
                class="app-btn-primary"
                :loading="savingPwd"
                @click="savePassword"
              >
                修改密码
              </el-button>
            </el-form-item>
          </el-form>

          <p class="note">
            改密码不会让其他设备上已登录的会话立即失效（无状态 JWT 无服务端会话可销），
            它们会在令牌到期后失效。
          </p>
        </el-card>

        <!-- 令牌信息 -->
        <el-card class="app-panel" shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">当前登录令牌</span>
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
              <dd :class="expiringSoon ? 'warn' : 'strong'">{{ remaining }}</dd>
            </div>
          </dl>

          <p class="note">
            令牌在前端只做展示解析（不验签）。是否有效由后端判定，无效时接口返回 401 并跳回登录页。
          </p>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: var(--app-content-max);
}

/* ---------------- 账号概览 ---------------- */
.hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  border-radius: var(--app-radius-inner);
  background: var(--app-gradient-primary);
  color: #fff;
  box-shadow: var(--app-shadow-panel);
}

.hero-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 22px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.26);
  flex: none;
}

.hero-main {
  min-width: 0;
}

.hero-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.hero-role {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.28);
}

/* 普通用户标签用更低的对比，避免和「管理员」抢注意力 */
.hero-role.is-user {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.18);
  font-weight: 500;
}

.hero-sub {
  margin-top: 6px;
  font-size: var(--app-fs-secondary);
  /* 0.92 而不是更低：渐变上过低的透明白会读不清（实测过） */
  color: rgba(255, 255, 255, 0.92);
}

.hero-sub strong {
  font-weight: 600;
}

.dot {
  margin: 0 6px;
  opacity: 0.6;
}

/* ---------------- 布局 ---------------- */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

/* 提交按钮右对齐，不占满整行 —— 这里是设置页，不是登录页 */
.submit-item :deep(.el-form-item__content) {
  justify-content: flex-end;
}

/* ---------------- 键值列表（令牌信息） ---------------- */
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
}

.kv-row dd.strong {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kv-row dd.warn {
  font-weight: 600;
  color: var(--app-status-warn-fg);
}

.note {
  margin: 14px 0 0;
  font-size: var(--app-fs-caption);
  line-height: 1.65;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 1000px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .hero {
    padding: 16px;
    gap: 12px;
  }

  .hero-avatar {
    width: 44px;
    height: 44px;
    border-radius: 13px;
    font-size: 18px;
  }
}
</style>
