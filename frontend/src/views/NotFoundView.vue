<script setup lang="ts">
/**
 * 404：地址不存在。
 *
 * <p>以前未知路径直接重定向到工作台 —— 用户手打错一个字母就被"传送"回首页，
 * 完全不知道发生了什么，还以为是系统抽风。现在明确告诉他地址不存在。
 *
 * <p>注意这一页渲染在 AppLayout 内部（路由是 `/` 的子路由），
 * 所以侧栏和顶栏都还在，用户可以直接从导航去别的页面。
 */
import StatusPage from '@/components/StatusPage.vue'
</script>

<template>
  <StatusPage
    code="404"
    title="页面不存在"
    description="这个地址没有对应的页面。可能是链接输错了，或者它已经被移除。"
  />
</template>
