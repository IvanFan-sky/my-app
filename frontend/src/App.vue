<script setup lang="ts">
/**
 * 根组件：只放一个路由出口。
 *
 * <p><b>这里不要手动渲染 AppLayout</b>。外壳是由**路由表**决定的：
 * `/` 这一层路由的 component 就是 AppLayout，子路由在它内部的 `<RouterView>` 里渲染。
 * 如果在这里再写一次 `<AppLayout v-else />`，AppLayout 内的 RouterView 处于 depth 0，
 * 会渲染出「匹配到的第一层组件」—— 也就是 AppLayout 它自己，
 * 结果是 AppLayout → AppLayout → 页面，**侧栏和顶栏各出现两遍**。
 * （这个双层嵌套的 bug 就是这么来的。）
 *
 * <p>现在的分工很干净：
 * <ul>
 *   <li>免登录页（/login、/register）是顶层路由，depth 0 直接渲染页面；</li>
 *   <li>登录态页面挂在 `/` 下，depth 0 渲染 AppLayout，depth 1 渲染具体页面。</li>
 * </ul>
 */
</script>

<template>
  <RouterView />
</template>
