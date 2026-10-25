import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 配置。
 *
 * <p><b>为什么单独占一套端口和数据库</b>：E2E 要起真实的后端与前端。
 * 如果复用开发用的 8080/3000 和 my_app 库，跑测试前必须先把正在开发的
 * 进程关掉 —— 那没人会愿意跑，最后这套测试就烂在仓库里。
 * 所以这里用 8081 + 3100 + my_app_e2e 库，与开发环境完全并行、互不干扰。
 *
 * <p><b>为什么 workers=1</b>：所有用例共用一个数据库，而登录失败计数、
 * token 版本号这类状态是按用户算的。并发跑会让「A 改密码把 B 的令牌作废」
 * 这类跨用例干扰变成随机失败。
 */
const BACKEND_PORT = 8081
const FRONTEND_PORT = 3100

const BACKEND_URL = `http://127.0.0.1:${BACKEND_PORT}`
const FRONTEND_URL = `http://127.0.0.1:${FRONTEND_PORT}`

export default defineConfig({
  testDir: './e2e',
  // 预热客户端，避开 Vite 依赖预构建引发的短暂重启（原因见该文件注释）
  globalSetup: './e2e/global-setup.ts',
  // 单个用例 45 秒：够包含登录跳转与表格渲染，又不至于卡住时白等
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  // CI 上重试一次：E2E 失败常见于环境抖动（容器刚起、CPU 打满），
  // 本地不重试 —— 本地红就该立刻看到
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]]
    : [['list']],
  use: {
    baseURL: FRONTEND_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: [
    {
      // E2E 专用后端：独立端口 + 独立库（首次运行由 JDBC 自动建库，
      // 表结构仍交给 Flyway，所以它和真实环境是同一套 schema）
      command: `mvn -q -DskipTests spring-boot:run -Dspring-boot.run.arguments=--server.port=${BACKEND_PORT}`,
      cwd: '../backend',
      url: `${BACKEND_URL}/api/health`,
      /*
       * 不复用已存在的服务。
       *
       * 一开始写的是 `!process.env.CI`（本地复用），结果撞上一种很难查的抖动：
       * 上一轮跑完时 Playwright 正在关停 dev server，下一轮启动时端口还在
       * LISTENING，复用判定通过 → 复用一个正在退出的进程 → 首个用例
       * ERR_CONNECTION_REFUSED，后面几个却正常。
       * 这两个端口是 E2E 专用的，本来就不该有别的进程占着，
       * 所以「端口被占就直接报错」比「猜测能否复用」更可靠。
       */
      reuseExistingServer: false,
      timeout: 240_000,
      env: {
        SPRING_DATASOURCE_URL:
          `jdbc:mysql://127.0.0.1:3306/my_app_e2e?createDatabaseIfNotExist=true` +
          '&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' +
          '&useSSL=false&allowPublicKeyRetrieval=true',
        DB_USERNAME: process.env.DB_USERNAME ?? 'root',
        DB_PASSWORD: process.env.DB_PASSWORD ?? 'root',
      },
    },
    {
      // 前端独立端口，并把 /api 代理指到上面那个后端。
      // 两个环境变量是新加的（见 vite.config.ts）：不这么做，E2E 就只能在
      // 「关掉开发服务器」的前提下跑
      command: 'pnpm dev',
      url: FRONTEND_URL,
      // 同上：不复用，避免复用到正在退出的 dev server
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        VITE_PORT: String(FRONTEND_PORT),
        VITE_API_PROXY_TARGET: BACKEND_URL,
      },
    },
  ],
})
