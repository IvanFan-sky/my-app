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
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p < sql\init.sql
```

⚠️ 该脚本会 `DROP TABLE sys_user`，重复执行会清空这张表。

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
├── sql/init.sql           # 建库建表 + 种子数据
└── docs/                  # 环境说明与一次性修复脚本
```

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

## 下一步

- 换成 MyBatis-Plus 的 XML 写复杂 SQL：见 `backend/src/main/resources/mapper/`
- 加页面：`frontend/src/views/` 建组件 + `frontend/src/router/index.ts` 注册路由
- 前端生产构建前，把 Element Plus 换成按需引入以减小体积（`frontend/src/main.ts` 有注释）
- 数据库变更改用 Flyway 管理，别再手改 `init.sql`
