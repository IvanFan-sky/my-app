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
    <div class="login-shell">
      <!-- 左：品牌区。纯装饰，窄屏隐藏（见样式里的媒体查询） -->
      <aside class="brand">
        <div class="brand-inner">
          <div class="brand-logo">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" aria-hidden="true">
              <path
                d="M12 2.5 21 7v10l-9 4.5L3 17V7l9-4.5Z"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linejoin="round"
              />
              <path
                d="M8.2 12.1 11 14.9l4.9-5.5"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>

          <h1 class="brand-title">my-app</h1>
          <p class="brand-desc">Vue 3 + Spring Boot + MySQL + Redis</p>

          <ul class="brand-points">
            <li>前后端分离，接口统一为 <code>{ code, message, data }</code></li>
            <li>JWT 无状态鉴权，密码以 BCrypt 哈希存储</li>
            <li>MyBatis-Plus 单表 CRUD 与分页开箱可用</li>
          </ul>
        </div>

        <!-- 装饰光斑：无文本含义，对读屏隐藏 -->
        <span class="deco deco-a" aria-hidden="true" />
        <span class="deco deco-b" aria-hidden="true" />
      </aside>

      <!-- 右：表单区 -->
      <section class="panel">
        <header class="panel-head">
          <h2 class="panel-title">欢迎回来</h2>
          <p class="panel-sub">请登录你的账号以继续</p>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              autocomplete="username"
              clearable
              @keyup.enter="submit"
            >
              <template #prefix>
                <svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="8" r="3.6" stroke="currentColor" stroke-width="1.6" />
                  <path
                    d="M4.8 20c.7-3.6 3.6-5.6 7.2-5.6s6.5 2 7.2 5.6"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                  />
                </svg>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keyup.enter="submit"
            >
              <template #prefix>
                <svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect
                    x="4.5"
                    y="10"
                    width="15"
                    height="10"
                    rx="2.4"
                    stroke="currentColor"
                    stroke-width="1.6"
                  />
                  <path
                    d="M8.2 10V7.6a3.8 3.8 0 0 1 7.6 0V10"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                  />
                </svg>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item class="submit-item">
            <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 开发期提示：种子账号写在这里，省得翻 sql/init.sql。
             部署前必须删掉这段，否则等于把账号信息挂在登录页上。 -->
        <el-alert type="info" :closable="false" class="hint" show-icon>
          <template #title>
            <span class="hint-title">测试账号</span>
          </template>
          <div class="hint-body">
            <code>alice / alice123</code>
            <code>bob / bob123</code>
            <code>carol / carol123</code>
          </div>
          <div class="hint-foot">见 sql/init.sql，上线前请删除本提示</div>
        </el-alert>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ============================================================
   配色：以 Element Plus 主色为基准，品牌区用同一色相的深色渐变。
   刻意不引入第三方配色变量，方便以后换主题时一起改。
   ============================================================ */
.login-page {
  --brand-from: #1f2a5a;
  --brand-to: #2b3f8f;
  --brand-accent: #6d8bff;

  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  /* 浅色底：表单卡片浮在上面，避免整屏纯白显得空 */
  background:
    radial-gradient(1100px 520px at 12% 8%, rgba(109, 139, 255, 0.14), transparent 62%),
    radial-gradient(900px 480px at 92% 92%, rgba(31, 42, 90, 0.1), transparent 60%),
    var(--el-fill-color-light);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 400px) minmax(0, 428px);
  width: 100%;
  max-width: 828px;
  border-radius: 20px;
  overflow: hidden;
  background: var(--el-bg-color);
  /* 两层阴影：近处收紧、远处扩散，比单层大模糊更立体 */
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.06),
    0 24px 60px -20px rgba(16, 24, 40, 0.28);
}

/* ---------------- 左侧品牌区 ---------------- */
.brand {
  position: relative;
  overflow: hidden;
  padding: 40px 34px;
  color: #fff;
  background: linear-gradient(158deg, var(--brand-from), var(--brand-to));
}

.brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  /* 垂直居中：右侧表单通常比品牌区内容高，居中后两边视觉基线更协调 */
  justify-content: center;
  height: 100%;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 22px;
  border-radius: 13px;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.brand-title {
  margin: 0;
  font-size: 27px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.brand-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.72);
}

.brand-points {
  margin: 30px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 13px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.86);
}

.brand-points li {
  position: relative;
  padding-left: 18px;
}

/* 用 ::before 画小圆点，省一层 DOM */
.brand-points li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-accent);
  box-shadow: 0 0 0 3px rgba(109, 139, 255, 0.22);
}

.brand-points code {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.12);
}

/* 装饰光斑 */
.deco {
  position: absolute;
  border-radius: 50%;
  filter: blur(46px);
  pointer-events: none;
}

.deco-a {
  width: 230px;
  height: 230px;
  right: -78px;
  top: -66px;
  background: rgba(109, 139, 255, 0.5);
}

.deco-b {
  width: 190px;
  height: 190px;
  left: -70px;
  bottom: -76px;
  background: rgba(126, 200, 255, 0.26);
}

/* ---------------- 右侧表单区 ---------------- */
.panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 44px 42px;
}

.panel-head {
  margin-bottom: 26px;
}

.panel-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-sub {
  margin: 7px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.field-icon {
  width: 17px;
  height: 17px;
  /* 图标用次级文字色；聚焦时靠 .el-input 自身的边框色表达状态即可，
     不必再写 :focus-within 去改图标颜色 */
  color: var(--el-text-color-placeholder);
}

/* el-form-item 的 content 是 flex 容器，按钮必须显式撑满，
   否则会缩成内容宽度（这是最初版本的实际问题） */
.submit-item :deep(.el-form-item__content) {
  width: 100%;
}

.submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  letter-spacing: 3px;
  border-radius: 9px;
  background: linear-gradient(100deg, var(--brand-to), var(--brand-accent));
  border: none;
  /* 只过渡需要的属性，避免 transition: all 带来意外动画 */
  transition: box-shadow 0.25s ease, transform 0.12s ease;
}

.submit-btn:not(.is-loading):hover {
  box-shadow: 0 10px 22px -8px rgba(43, 63, 143, 0.6);
}

.submit-btn:not(.is-loading):active {
  transform: translateY(1px);
}

.hint {
  margin-top: 22px;
  border-radius: 10px;
}

.hint-title {
  font-size: 13px;
  font-weight: 600;
}

.hint-body {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
}

.hint-body code {
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 12px;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-regular);
}

.hint-foot {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ---------------- 响应式 ---------------- */
/* 窄屏（含竖屏平板）隐藏品牌区，只留表单，避免两栏被压扁 */
@media (max-width: 760px) {
  .login-page {
    padding: 16px;
  }

  .login-shell {
    grid-template-columns: minmax(0, 1fr);
    max-width: 440px;
  }

  .brand {
    display: none;
  }

  .panel {
    padding: 32px 24px;
  }
}

/* 尊重「减少动态效果」的系统设置 */
@media (prefers-reduced-motion: reduce) {
  .submit-btn {
    transition: none;
  }

  .submit-btn:not(.is-loading):active {
    transform: none;
  }
}
</style>
