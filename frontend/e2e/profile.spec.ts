import { expect, test } from '@playwright/test'

/**
 * 「注册 → 改密码 → 旧密码失效 → 新密码可登录」的完整链路。
 *
 * <p>这条链最有价值的地方在最后两步：改密码会把 token_version +1、
 * 作废该用户全部令牌，同时后端会返回一对新令牌。任何一环错位，
 * 表现都是「改完密码就被踢出去，或者旧密码还能登」—— 单测覆盖了逻辑，
 * 但覆盖不了「前端到底有没有把新令牌存下来」。
 */
test('注册新账号后改密码：旧密码失效，新密码可登录', async ({ page }) => {
  // 用户名带时间戳，保证每次跑都是全新账号（E2E 库会一直留着历史数据）
  const username = `e2e${Date.now().toString(36)}`
  const oldPassword = 'e2e-old-123'
  const newPassword = 'e2e-new-456'

  // ---------- 注册即登录 ----------
  await page.goto('/register')
  await page.getByPlaceholder('请输入用户名，最长 64 个字符').fill(username)
  await page.getByPlaceholder('6~72 个字符').fill(oldPassword)
  await page.getByPlaceholder('请再次输入密码').fill(oldPassword)
  await page.getByPlaceholder('选填，用于后续找回密码').fill(`${username}@example.com`)
  await page.locator('.auth-submit').click()

  // 后端注册成功直接签发令牌，前端也直接进工作台，不需要再登一次
  await page.waitForURL(/\/dashboard$/)

  // ---------- 改密码 ----------
  await page.goto('/profile')
  await page.getByPlaceholder('请输入当前密码').fill(oldPassword)
  await page.getByPlaceholder('6~72 个字符').fill(newPassword)
  await page.getByPlaceholder('请再次输入新密码').fill(newPassword)
  await page.getByRole('button', { name: '修改密码' }).click()

  await expect(page.locator('.el-message').filter({ hasText: '密码已修改' })).toBeVisible()

  // ---------- 换一台「设备」：清掉本地会话 ----------
  // 用 localStorage.clear() 而不是走退出登录的弹窗：这里要验证的是密码，
  // 弹窗是另一条链路（顶部下拉 → 确认框），混在一起会让失败原因不明确
  await page.evaluate(() => localStorage.clear())

  // ---------- 旧密码必须失效 ----------
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(oldPassword)
  await page.locator('.auth-submit').click()
  await expect(page.locator('.el-message').first()).toContainText('用户名或密码错误')

  // ---------- 新密码可以登录 ----------
  // 登录失败时页面会清空密码框（只清密码，保留用户名），所以直接重填密码即可
  await page.getByPlaceholder('请输入密码').fill(newPassword)
  await page.locator('.auth-submit').click()
  await page.waitForURL(/\/dashboard$/)
})
