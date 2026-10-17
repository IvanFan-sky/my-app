<script setup lang="ts">
/**
 * 403：已登录，但当前角色没有权限。
 *
 * <p>和 401 的区别要说清楚：401 是"你没登录"，会把人送去登录页；
 * 403 是"你登录了，但这个页面不归你" —— 送登录页是错误引导，
 * 用户会以为自己的账号出了问题，反复重登也没用。
 *
 * <p>两种触发路径都会到这里：
 * <ul>
 *   <li>路由守卫：直接访问 meta.requiresAdmin 的页面；</li>
 *   <li>接口 403：角色在中途被降级（见 bootstrap.ts 注册的 handler）。</li>
 * </ul>
 */
import StatusPage from '@/components/StatusPage.vue'

defineProps<{
  /** 来自 query 的具体说明（例如"用户管理需要管理员权限"），可选 */
  reason?: string
}>()
</script>

<template>
  <StatusPage
    code="403"
    title="没有访问权限"
    :description="
      reason ||
      '当前账号的角色无权访问这个页面。如果确实需要，请联系管理员调整角色（管理员不能自行修改自己的角色）。'
    "
  />
</template>
