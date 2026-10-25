import { chromium } from '@playwright/test'

/**
 * 全局预热：在第一个用例之前，先自己把前端首页加载一遍。
 *
 * <p><b>为什么需要</b>：Vite 的依赖预构建发生在「第一次真正加载页面」时，
 * 期间 dev server 会短暂重启。Playwright 的 webServer 健康检查只发一个
 * HTTP GET，那时还看不出问题 —— 但第一个用例的 `page.goto` 正好撞上重启，
 * 报 `ERR_CONNECTION_REFUSED`，而后面所有用例都正常。
 * 这个抖动是必现的（每轮都只有第一个用例红），所以不能靠 retries 掩盖。
 *
 * <p>预热做两件事：等 `/login` 连续两次加载成功（中间那次可能刚好撞上重启），
 * 失败就重试。这样正式用例一上来面对的就是热好的服务。
 */

const FRONTEND_URL = `http://127.0.0.1:${process.env.VITE_PORT ?? 3100}`

const MAX_ATTEMPTS = 30

export default async function globalSetup() {
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle', timeout: 5000 })
        // 第二次用来确认服务已经稳定：第一次可能刚触发预构建，紧接着就重启
        await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle', timeout: 5000 })
        return
      } catch {
        await new Promise((resolve) => setTimeout(resolve, 1000))
      }
    }
    throw new Error(`E2E 前端预热失败：重试 ${MAX_ATTEMPTS} 次后 ${FRONTEND_URL}/login 仍不可用`)
  } finally {
    await browser.close()
  }
}
