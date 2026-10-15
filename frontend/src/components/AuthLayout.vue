<script setup lang="ts">
/**
 * 登录页与注册页共用的页面外壳：卡片、品牌区、装饰光斑、表单标题。
 *
 * <p><b>为什么要有这个组件</b>：两个页面若各写一套样式，色板、圆角、按钮、
 * 间距会各自漂移，最终看起来像两个不同产品。把外壳与共享表单样式收在这里，
 * 「统一设计语言」就由结构保证，而不是靠人记得两边同步改。
 *
 * <p>两处刻意的取舍：
 * <ol>
 *   <li>色板用 CSS 变量定义在根元素上，两个页面的表单样式都引用它。</li>
 *   <li>下面的 form 相关样式**不是 scoped** —— 因为表单是父组件通过插槽传进来的，
 *       scoped 样式只对当前组件的模板生效，管不到插槽内容。代价是全局作用域，
 *       所以类名一律加 <code>auth-</code> / <code>field-</code> 前缀避免外溢。
 *       这是 Vue 里共享插槽样式的标准做法，不是忘了写 scoped。</li>
 * </ol>
 */
defineProps<{
  /** 表单区主标题，如「欢迎回来」/「创建账号」 */
  title: string
  /** 表单区副标题 */
  subtitle: string
}>()
</script>

<template>
  <div class="auth-layout">
    <div class="auth-shell">
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

      <!-- 右：表单区。内容由各页面通过默认插槽提供 -->
      <section class="panel">
        <header class="panel-head">
          <h2 class="panel-title">{{ title }}</h2>
          <p class="panel-sub">{{ subtitle }}</p>
        </header>

        <slot />

        <!-- 底部切换链接（去注册 / 去登录） -->
        <div v-if="$slots.switch" class="auth-switch">
          <slot name="switch" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ============================================================
   配色：以 Element Plus 主色为基准，品牌区用同一色相的深色渐变。
   定义在根元素上，下面的非 scoped 表单一并继承（CSS 变量可继承）。
   ============================================================ */
.auth-layout {
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

.auth-shell {
  display: grid;
  /* 注册表单比登录多两个字段，右栏略宽一点；两页共用同一个最大宽度，观感一致 */
  grid-template-columns: minmax(0, 400px) minmax(0, 452px);
  width: 100%;
  max-width: 852px;
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

/* ---------------- 响应式 ---------------- */
/* 窄屏（含竖屏平板）隐藏品牌区，只留表单，避免两栏被压扁 */
@media (max-width: 760px) {
  .auth-layout {
    padding: 16px;
  }

  .auth-shell {
    grid-template-columns: minmax(0, 1fr);
    max-width: 460px;
  }

  .brand {
    display: none;
  }

  .panel {
    padding: 32px 24px;
  }
}
</style>

<!--
  下面是「共享表单样式」。刻意**不写 scoped**：
  表单元素是父组件插进来的，scoped 样式碰不到插槽内容。
  所有类名都带 auth- / field- 前缀，把全局污染限制在可识别的范围内。
-->
<style>
/* 输入框前缀图标 */
.field-icon {
  width: 17px;
  height: 17px;
  /* 图标用次级文字色；聚焦时靠 .el-input 自身的边框色表达状态即可，
     不必再写 :focus-within 去改图标颜色 */
  color: var(--el-text-color-placeholder);
}

/* el-form-item 的 content 是 flex 容器，按钮必须显式撑满，
   否则会缩成内容宽度。
   用专门的 .auth-submit-item 类名而不是 :last-of-type —— 后者在有人往表单末尾
   再加一个表单项时会静默失效，而且从类名看不出「这里需要撑满」的意图。 */
.auth-form .auth-submit-item .el-form-item__content {
  width: 100%;
}

.auth-submit {
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

.auth-submit:not(.is-loading):hover {
  box-shadow: 0 10px 22px -8px rgba(43, 63, 143, 0.6);
}

.auth-submit:not(.is-loading):active {
  transform: translateY(1px);
}

.auth-hint {
  margin-top: 22px;
  border-radius: 10px;
}

.auth-hint-title {
  font-size: 13px;
  font-weight: 600;
}

.auth-hint-body {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
}

.auth-hint-body code {
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 12px;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-regular);
}

.auth-hint-foot {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 底部切换链接，两个页面共用同一版式 */
.auth-switch {
  margin-top: 18px;
  font-size: 13px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.auth-switch a {
  color: var(--brand-to);
  font-weight: 600;
  text-decoration: none;
}

.auth-switch a:hover {
  color: var(--brand-accent);
  text-decoration: underline;
}

/* 尊重「减少动态效果」的系统设置 */
@media (prefers-reduced-motion: reduce) {
  .auth-submit {
    transition: none;
  }

  .auth-submit:not(.is-loading):active {
    transform: none;
  }
}
</style>
