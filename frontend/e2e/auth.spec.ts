import { expect, test } from '@playwright/test'
import { ADMIN, NORMAL, login, loginAndWait } from './support/login'

/**
 * 登录与入口可见性。
 *
 * <p>这些用例跑在真实浏览器 + 真实后端上，验证的是「前后端拼起来还能不能用」——
 * 单测能保证 store 逻辑对、接口返回对，但保证不了「登录后真的跳走了」、
 * 「非管理员真的看不到菜单」。
 */
test.describe('登录与权限入口', () => {
  test('管理员登录后进入工作台，顶栏与侧栏都正确', async ({ page }) => {
    await loginAndWait(page, ADMIN.username, ADMIN.password)

    // 页面标题来自路由 meta.title，与浏览器标签同步
    await expect(page.locator('.ph-title')).toHaveText('工作台')

    /*
     * 顶栏显示 displayName = 昵称 || 用户名。alice 的昵称由 V2 迁移写成「爱丽丝」，
     * 所以这里**不写死具体文案** —— 昵称属于数据，改迁移脚本不该让 E2E 变红。
     * 要守的是「真的拿到了当前用户」，未登录态会显示「未登录」。
     */
    await expect(page.locator('.user-name')).toBeVisible()
    await expect(page.locator('.user-name')).not.toHaveText('未登录')

    // 管理员能看到两个管理入口
    const nav = page.getByRole('navigation', { name: '主导航' })
    await expect(nav.getByText('用户管理')).toBeVisible()
    await expect(nav.getByText('审计日志')).toBeVisible()
  })

  test('密码错误时给出可读提示，且停留在登录页', async ({ page }) => {
    /*
     * 刻意用一个不存在的用户名，而不是拿 alice 输错密码。
     * 登录失败计数是按用户名累计的：连续 5 次会在 Redis 里锁 3 分钟，
     * 本地连着跑几轮就会把 alice 锁住，后面所有用例跟着挂。
     * 用一个一次性的用户名，既不触发锁定，也顺带覆盖了「用户不存在」这条分支
     * （它和「密码错误」返回的文案必须完全一样）。
     */
    await login(page, 'e2e-no-such-user', 'whatever123')

    await expect(page.locator('.el-message').first()).toContainText('用户名或密码错误')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('普通用户看不到管理入口；直接访问 /users 会落到 403 页', async ({ page }) => {
    await loginAndWait(page, NORMAL.username, NORMAL.password)

    const nav = page.getByRole('navigation', { name: '主导航' })
    // 菜单是按 auth.isAdmin 过滤出来的（见 AppSidebar 的 navItems）
    await expect(nav.getByText('用户管理')).toHaveCount(0)

    // 手输地址也进不去：路由守卫会把非管理员送到 403 而不是弹个一闪而过的提示
    await page.goto('/users')
    await expect(page).toHaveURL(/\/403/)
    await expect(page.getByText('没有访问权限')).toBeVisible()
  })
})
