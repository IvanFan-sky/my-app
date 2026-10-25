import { expect, test } from '@playwright/test'
import { ADMIN, loginAndWait } from './support/login'

/**
 * 导出 CSV：从点按钮到浏览器真的拿到下载文件。
 *
 * <p>这条链路单测覆盖不到：后端是流式响应、前端是 blob 下载，
 * 中间还隔着「Content-Type 校验」和「a 标签触发下载」。
 * 断言的落点是**下载事件的文件名**——它同时证明了三件事：
 * 请求成功、响应通过了类型校验、前端把 Blob 真的变成了文件。
 */
test('管理员在用户管理页导出 CSV，浏览器拿到正确命名的文件', async ({ page }) => {
  await loginAndWait(page, ADMIN.username, ADMIN.password)
  await page.goto('/users')

  // 先确认表格真的渲染出了数据：空表格也能「导出成功」，那样断言就失去意义了
  await expect(page.locator('.el-table__row').first()).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button').filter({ hasText: '导出 CSV' }).click()
  const download = await downloadPromise

  // 文件名由后端拼：users-yyyyMMdd-HHmmss.csv
  expect(download.suggestedFilename()).toMatch(/^users-\d{8}-\d{6}\.csv$/)
})
