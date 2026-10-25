import type { Page } from '@playwright/test'

/**
 * E2E 共用的登录动作。
 *
 * <p>不放在 *.spec.ts 里：Playwright 默认只把 `*.spec.ts` / `*.test.ts` 当用例，
 * 这个文件只是被 import 的工具。
 */

/** 种子管理员（Flyway V1 写入），E2E 库首次启动时自动存在 */
export const ADMIN = { username: 'alice', password: 'alice123' }

/** 种子普通用户，用来验证「非管理员看不到管理入口」 */
export const NORMAL = { username: 'bob', password: 'bob123' }

/** 填表并提交，不等待跳转 —— 失败场景要靠调用方自己断言 */
export async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.locator('.auth-submit').click()
}

/** 登录并等到工作台。登录成功会 router.replace('/') → 重定向到 /dashboard */
export async function loginAndWait(page: Page, username: string, password: string) {
  await login(page, username, password)
  await page.waitForURL(/\/dashboard$/)
}
