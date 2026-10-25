# my-app

全栈 Web 应用骨架：**Vue 3 + Vite + TypeScript** 前端，**Spring Boot + MyBatis-Plus + MySQL + Redis** 后端。

这个骨架已经能跑通一条完整的链路：前端表格 ← 分页查询接口 ← MySQL 表 `sys_user`，
并且通过 `/api/health` 真实探测 Redis 连通性。在此之上直接加业务即可。

> **给 AI agent 的说明请看 [AGENTS.md](AGENTS.md)** —— 那里有本机环境事实、构建命令和改代码的约定。

## 端口

| 服务 | 端口 | 地址 |
|---|---|---|
| 后端 | 8080 | <http://127.0.0.1:8080> |
| 前端开发服务器 | **3000** | <http://127.0.0.1:3000> |

> ⚠️ 前端**不用** Vite 默认的 5173：本机 Hyper-V 预留了 4990–5188 端口段，
> 5173/5174 都在其中，绑定会报 `EACCES`。已改用 3000。

---

## 环境要求

| 组件 | 版本 | 本机位置 |
|---|---|---|
| JDK | 21 | `F:\JavaTool\JDK21` |
| Maven | 3.10.0 | `F:\JavaTool\apache-maven-3.10.0` |
| Node.js | ≥ 22（本机 24.21.0） | `F:\JavaTool\NodeJS` |
| pnpm | 10.x（本机 10.5.2） | `F:\JavaTool\NodeJS\node_global` |
| MySQL | 8.4.11 | 服务名 `MySQL84`，`127.0.0.1:3306` |
| Redis | 8.10.2 | 服务名 `Redis`，`127.0.0.1:6379` |

MySQL 与 Redis 都注册成了 Windows 服务，默认开机自启。检查状态：

```powershell
Get-Service MySQL84, Redis
```

---

## 首次运行（按顺序做）

### 1. 建库

```powershell
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p -e "CREATE DATABASE IF NOT EXISTS my_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
```

**只需要建库。** 表结构与种子账号由 **Flyway** 在应用启动时自动创建，
脚本在 `backend/src/main/resources/db/migration/`，执行记录写在 `flyway_schema_history` 表。

- 变更表结构：**新增** `V<n>__说明.sql`，不要改已发布的迁移脚本
  （`validate-on-migrate` 会校验 checksum，改了会让已有环境启动失败）。
- 已有数据的库（表是手工建的）会自动在 V2 打基线并跳过 V1/V2，不会丢数据。

### 2. 数据库密码

本机已配好：密码写在 **`backend/src/main/resources/application-local.yml`**，
该文件**已被 `.gitignore` 排除，不会进版本库**，并由 `application.yml` 里的
`spring.profiles.active: local` 自动加载，因此 `mvn spring-boot:run` 开箱即用。

换机器或换密码时改这个文件即可，或在启动前设 `DB_PASSWORD` 环境变量（优先级更高）。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

验证：<http://127.0.0.1:8080/api/health> 应返回 `redis: "pong"`。

### 4. 启动前端

```powershell
cd frontend
pnpm install
pnpm dev
```

打开 <http://127.0.0.1:3000>，页面上会显示后端与 Redis 的连通状态，以及 `sys_user` 的种子数据。

---

## 目录结构

```
my-app/
├── AGENTS.md              # AI agent 与协作者的工作约定（先读这个）
├── README.md
├── backend/               # Spring Boot 服务
│   ├── pom.xml
│   └── src/main/java/com/example/myapp/
│       ├── MyAppApplication.java
│       ├── common/        # ApiResponse / BizException / 全局异常处理
│       ├── config/        # MyBatis-Plus 等配置
│       ├── controller/    # REST 接口
│       ├── entity/        # 数据库实体
│       ├── mapper/        # MyBatis-Plus Mapper
│       └── service/       # 业务层
├── frontend/              # Vue 3 应用
│   └── src/
│       ├── api/           # axios 封装与接口定义
│       ├── router/        # 路由
│       ├── stores/        # Pinia 状态
│       └── views/         # 页面
├── backend/src/main/resources/db/migration/   # Flyway 迁移（表结构的唯一来源）
└── docs/                  # 环境说明与一次性修复脚本
```

---

## 前端工具链

```powershell
cd frontend
pnpm lint          # ESLint：未用变量、Vue API 误用、模板写法
pnpm format:check  # Prettier：格式是否统一
pnpm type-check    # vue-tsc：类型
pnpm test          # Vitest：拦截器 / store / api 封装的单元测试
pnpm size-check    # 产物体积守卫（需先 build）
pnpm build         # 类型检查 + 生产构建
```

三者分工：**ESLint 管「对不对」、Prettier 管「好不好看」、vue-tsc 管类型**。
`eslint-config-prettier` 已关掉 ESLint 的格式类规则，两者不会互相打架。
根目录有 `.editorconfig`，IDE 会自动套用缩进与换行约定。

**这是按需引入的产物基线**（Element Plus 只按实际用到的组件打包）：
JS 约 593 KB / CSS 约 151 KB / dist 约 0.73 MB。改造前是 1.47 MB（单个 1.1 MB 的 chunk）。
`pnpm size-check` 会读 `dist/assets` 的实际大小并在超阈值时失败，CI 构建后也会跑它；
若某次改构建配置后体积明显变大，多半是 `manualChunks` 又把整个 element-plus 强制分进去了
（细节见 AGENTS.md 第 5 节）。

## 测试


```powershell
cd backend
mvn test          # 集成测试，需要本机 MySQL + Redis 已启动
```

测试连的是**独立的 `my_app_test` 库**（由 `src/test/resources/application-test.yml` 指定，
首次运行自动建库、表结构仍由 Flyway 生成），不会碰开发库 `my_app`。

测试是 `@SpringBootTest` + MockMvc 的**集成**测试，没有把 DB/Redis 换成假的 ——
要守住的东西（权限边界、缓存失效、空值覆盖、登录锁定）全都发生在框架协作处，
换成 mock 会全绿但线上照样出事。

覆盖范围：认证流程、权限边界（含提权尝试与管理员自我保护）、个人中心、
登录失败锁定、CRUD / 分页搜索 / 缓存命中。

CI 见 `.github/workflows/ci.yml`：push 与 PR 时自动跑后端测试（带 MySQL、Redis 服务容器）
以及前端 `type-check` + `build`。

---
## 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查，含 Redis 真实读写 |
| GET | `/api/users` | 分页查询，参数 `username` / `current` / `size` |
| GET | `/api/users/{id}` | 单个查询 |
| POST | `/api/users` | 新增 |
| PUT | `/api/users/{id}` | 修改 |
| DELETE | `/api/users/{id}` | 逻辑删除 |

统一响应体：

```json
{ "code": 0, "message": "ok", "data": {} }
```

`code` 为 `0` 表示成功；非 `0` 表示业务失败（HTTP 状态码仍是 200）。

---

## 许可

[MIT](LICENSE) —— 可自由使用、修改、商用，保留版权声明即可。

## 下一步

- 换成 MyBatis-Plus 的 XML 写复杂 SQL：见 `backend/src/main/resources/mapper/`
- 加页面：`frontend/src/views/` 建组件 + `frontend/src/router/index.ts` 注册路由
- 前端生产构建前，把 Element Plus 换成按需引入以减小体积（`frontend/src/main.ts` 有注释）
- 登录失败限制目前按用户名计数，公网部署建议再叠加 IP 维度或验证码
