# AGENTS.md — my-app

本文件是这个仓库的**工作约定**，面向在本仓库里干活的 AI agent 和人类协作者。
动手前先读完「本机环境事实」和「改动前必做」两节，否则容易踩到本机特有的坑。

---

## 1. 项目概述

全栈 Web 应用：**Vue 3 + Vite + TypeScript** 前端，**Spring Boot + MyBatis-Plus + MySQL + Redis** 后端。
前后端分目录存放，各自独立构建；开发期前端通过 Vite 代理访问后端，不依赖后端配 CORS。

当前状态：骨架已跑通一条完整链路（前端表格 ← 分页接口 ← MySQL，外加 Redis 健康探测）。
业务功能从零开始加。

---

## 2. 技术栈与版本（已锁定，改版本要说明理由）

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 21（LTS） |
| 后端框架 | Spring Boot | 3.5.16 |
| ORM | MyBatis-Plus | 3.5.17（`mybatis-plus-spring-boot3-starter`） |
| 数据库 | MySQL | 8.4.11，字符集 `utf8mb4` |
| 缓存 | Redis | 8.10.2 |
| 构建 | Maven | 3.10.0 |
| 前端框架 | Vue | 3.5.x |
| 构建工具 | Vite | 6.x |
| 语言 | TypeScript | 5.7.x |
| UI 库 | Element Plus | 2.9.x |
| 状态管理 | Pinia | 2.3.x |
| HTTP | axios | 1.7.x |
| 包管理 | pnpm | 10.x |
| JWT | jjwt | 0.12.5（`jjwt-api` compile + `jjwt-impl` / `jjwt-jackson` runtime） |
| 密码哈希 | spring-security-crypto | 由 Boot BOM 管理（6.5.x），**只引这一个模块** |
| 数据库迁移 | Flyway | 由 Boot BOM 管理（11.x），另需 `flyway-mysql` 模块 |

**为什么 Spring Boot 停留在 3.5.x 而不是 4.x**：3.5 是 3.x 最后一条线，中文生态
（MyBatis-Plus、Druid、Hutool 等）与绝大多数教程都对齐它；4.x 需要 Spring Framework 7，
部分三方库尚未跟上。升级 4.x 是独立任务，需单独评估并跑通全部测试，不要顺手升。

**为什么用 MyBatis-Plus 而不是 JPA**：本项目定位是国内主流业务后端，MyBatis-Plus 的
单表 CRUD + 条件构造器更贴近实际团队习惯，且 SQL 可控。

**为什么引 jjwt 而不是自己写 HMAC**：JWT 的坑（alg 混淆、`exp` 时区、
base64url 补位）自己写一遍很容易留下安全漏洞，用成熟库更稳。
0.12.x 与 0.11 的 API 不兼容（`Jwts.builder().subject()` 取代了 `setSubject()`），
照着旧教程写会直接编译不过。

**为什么只要 `spring-security-crypto` 而不要 `spring-boot-starter-security`**：
本项目鉴权走自定义拦截器，只需要一个 `BCryptPasswordEncoder`。引完整 starter 会装配
整套 servlet 过滤器链和默认表单登录页，反而得再写配置把它关掉。

---

## 3. 本机环境事实（实测，非推测）

平台：Windows。**Shell 是 Windows PowerShell 5.1，没有安装 PowerShell 7（`pwsh` 不是命令）。**
写脚本时不要用 PS7 专有语法。

| 组件 | 事实 |
|---|---|
| JDK | 21.0.8（Oracle），`F:\JavaTool\JDK21` |
| **`JAVA_HOME`** | **为空**。Maven 靠 PATH 找到 JDK 能正常工作；但某些工具/IDE 需要它，遇到问题先补这个变量 |
| Maven | 3.10.0，`F:\JavaTool\apache-maven-3.10.0` |
| Maven 本地仓库 | **`F:\JavaTool\Maven\maven_repository`**（不是 `~/.m2/repository`） |
| Maven 镜像 | 全局 `conf\settings.xml` 已配阿里云 `https://maven.aliyun.com/repository/central` |
| Node.js | v24.21.0，`F:\JavaTool\NodeJS` |
| npm / pnpm | 11.3.0 / 10.5.2 |
| npm registry | `https://registry.npmmirror.com`（`~/.npmrc` 已配国内镜像） |
| Git | 2.51.0 |
| MySQL | 8.4.11，**Windows 服务名 `MySQL84`**，`127.0.0.1:3306`，datadir `F:/JavaTool/MySQL84/Data`，配置 `F:/JavaTool/MySQL84/my.ini` |
| Redis | 8.10.2，**Windows 服务名 `Redis`**，`127.0.0.1:6379`，**未设密码**，数据目录 `F:/JavaTool/Redis-x64-8.10.2/data` |
| PowerShell 执行策略 | `LocalMachine = RemoteSigned`。新建的 `.ps1` 可能因签名/封锁无法直接运行 |

MySQL 与 Redis 都是自启服务。确认：

```powershell
Get-Service MySQL84, Redis
Test-NetConnection 127.0.0.1 -Port 3306
Test-NetConnection 127.0.0.1 -Port 6379
```

---

## 4. 常用命令

所有命令都在仓库根目录或标注的子目录下执行。

### 后端（`backend/`）

```powershell
mvn -q compile                 # 只编译，最快的语法检查
mvn -DskipTests package        # 打包，跳过测试
mvn test                       # 跑测试（需要 MySQL + Redis 可用且已建库）
mvn spring-boot:run            # 启动服务，默认 8080
mvn -U ...                     # 强制重新下载依赖（依赖疑似损坏时用）
```

### 前端（`frontend/`）

```powershell
pnpm install
pnpm dev                       # 开发服务器，3000，带 /api 代理
pnpm type-check                # vue-tsc 类型检查（不产出文件）
pnpm build                     # 类型检查 + 生产构建
pnpm preview                   # 预览构建产物
```

> **端口固定为 3000，不要改回 Vite 默认的 5173。** 本机 Hyper-V 预留了
> 4990–5188 端口段，5173/5174 都在其中，绑定会报 `listen EACCES`。
> 查保留区间：`netsh interface ipv4 show excludedportrange protocol=tcp`。
>
> **另注**：在 DSH 的 workspace-write 沙箱下 `pnpm dev` 会因 esbuild
> 需要以管道方式启动子进程而报 `spawn EPERM`。那是沙箱边界，不是项目问题；
> 用完全权限运行，或在你自己的终端里跑。

### 数据库

```powershell
# 只需建库（表结构与种子数据由 Flyway 在应用启动时自动创建）
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p -e "CREATE DATABASE IF NOT EXISTS my_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 进客户端
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p my_app
```

> **表结构由 Flyway 管理，不要再手写建表 SQL。**
> 启动应用时 Flyway 自动执行 `backend/src/main/resources/db/migration` 下未应用的脚本，
> 执行记录存在 `flyway_schema_history` 表。新增/变更表结构 = 新增一个 `V<n>__说明.sql`。

### Redis

```powershell
& 'F:\JavaTool\Redis-x64-8.10.2\redis-cli.exe' ping    # 期望 PONG
```

### Git 远程与推送

| 项 | 值 |
|---|---|
| 远程 | `origin` → `https://github.com/IvanFan-sky/my-app.git`（public） |
| 分支 | `main`，跟踪 `origin/main` |

**认证方式：HTTPS + Git Credential Manager**（系统级 `credential.helper=manager`，
位于 `F:\JavaTool\Git\mingw64\bin\git-credential-manager.exe`）。
首次推送在浏览器里完成一次 GitHub 授权，凭据存入 Windows 凭据管理器，之后免密。

两个实测得到的注意点：

- **GCM 在无终端的环境下会判定为非交互直接放弃**。要让它走浏览器授权，
  必须显式设 `GCM_INTERACTIVE=always`（同时不要设 `GIT_TERMINAL_PROMPT=0`）。
  反之，脚本化推送若想快速失败而不挂起，就设 `GIT_TERMINAL_PROMPT=0`。
- **本机 SSH 私钥存在，但未在 GitHub 登记**：`ssh -T git@github.com` 返回
  `Permission denied (publickey)`（22 与 443 端口均如此）。所以**不要**把远程地址改成
  `git@github.com:IvanFan-sky/my-app.git`，除非先去
  <https://github.com/settings/ssh/new> 登记 `~/.ssh/id_rsa.pub`。

另注：全局 `~/.gitconfig` 里有 `http.sslverify=false`，GCM 每次会打印 TLS 安全警告。
这是历史遗留配置，与推送是否成功无关。

**推送前自查密码未入库**（应为空输出）：

```powershell
git ls-files | Select-String 'application-local'
```

---

## 5. 目录结构

```
backend/src/main/java/com/example/myapp/
├── MyAppApplication.java   # 启动类，@MapperScan("com.example.myapp.mapper")
├── common/                 # ApiResponse（统一响应）、BizException、ErrorCode、GlobalExceptionHandler
├── config/                 # MybatisPlusConfig（分页插件）、WebMvcConfig（鉴权拦截器注册）、
│                           # JwtProperties（app.jwt.* 配置）、SecurityBeanConfig（PasswordEncoder）
├── controller/             # 只做参数校验与编排，不写业务逻辑
├── cache/                  # UserCache（用户信息缓存）、LoginAttemptCache（登录失败计数与锁定），
│                           # 两者都必须容忍 Redis 故障（fail-open）
├── dto/                    # 对外请求体与响应体（Form / VO），不暴露 entity
├── entity/                 # 与数据库表一一对应
├── mapper/                 # 继承 BaseMapper，复杂 SQL 放 resources/mapper/*.xml
├── security/               # JwtTokenProvider（签发/解析）、AuthInterceptor（校验令牌）、
│                           # RoleInterceptor + RequireRole（按端点声明的角色校验）、
│                           # AuthResponses（拦截器写错误响应）、
│                           # CurrentUser + LoginUser + CurrentUserArgumentResolver（注入登录用户）
└── service/ + service/impl # 业务逻辑，继承 IService / ServiceImpl

backend/src/main/resources/db/migration/   # Flyway 迁移脚本，表结构的唯一来源
├── V1__init.sql            # 建表 + 种子账号
└── V2__profile_role.sql    # 增加 nickname / role / bio

backend/src/test/java/com/example/myapp/
├── MyAppApplicationTests.java  # 容器冒烟
├── support/ApiTestSupport.java # 集成测试基类（发请求/断言/造数据/清理）
└── api/                        # AuthFlowTest / PermissionTest / ProfileTest /
                                # LoginLockoutTest / UserCrudTest

frontend/src/
├── api/          # http.ts（axios 实例 + 拦截器）、auth.ts（登录/注册接口）、user.ts（接口定义）
├── components/
│   ├── layout/   # AppLayout（外壳）、AppSidebar（侧栏）、AppHeader（顶栏+面包屑）、
│   │             # PageHeader（页面标题区）、StatCard（统计卡）
│   ├── AuthLayout.vue   # 登录/注册共用外壳与共享表单样式
│   ├── FieldIcon.vue    # 登录/注册表单的输入框图标
│   ├── AppIcon.vue      # 后台通用图标集（name 驱动）
│   └── icons.ts         # AppIconName 类型（<script setup> 不能导出类型，故单独成文件）
├── styles/       # tokens.css（全局设计令牌：品牌色、状态色、布局尺寸、字号梯度、阴影）
├── router/       # 路由表 + 未登录守卫（public 页面：/login、/register）
├── stores/       # Pinia store：auth.ts 登录态、app.ts 侧栏折叠与连通性
├── utils/        # jwt.ts（仅用于展示的令牌载荷解析）
├── views/        # 页面：Login/Register/Dashboard/Users/Profile/Settings
├── bootstrap.ts  # 把 api 层的令牌注入与 401 回调接到 store / 路由上
├── App.vue       # 只决定用哪种外壳（免登录页 vs AppLayout）
└── main.ts       # 应用装配
```

> **登录页与注册页的样式为什么集中在 `components/AuthLayout.vue`**：两个页面若各自
> 写一套，色板、圆角、按钮、间距会各自漂移，最后看起来像两个产品。外壳与共享表单样式
> 收在 AuthLayout 里，「设计语言统一」由结构保证而不是靠人同步。
> **设计令牌（品牌色、卡片圆角、阴影、主按钮渐变）统一放在 `src/styles/tokens.css` 的 `:root`**，
> 登录页、注册页、用户管理页都只引用变量、不写字面值。某一页需要新颜色/新尺寸时，
> 先往 tokens.css 加，不要在页面里就地写死 —— 否则「三个页面像三个产品」会从这里开始。
>
> **配色约定（换肤时必读）**：品牌色是青绿 → 天蓝（`--brand-from` / `--brand-to`），
> 但**承载白字的大色块不能用最亮的青**。当前值是按对比度反推的：
> 白字 on `--brand-from`(#0f766e) = 5.47:1、on `--brand-to`(#0f7fa6) = 4.56:1，均达 WCAG AA。
> 亮青 `--brand-accent`(#22d3ee) 只允许用在**装饰**（光斑、小圆点、选中亮条），
> 浅底上的文字/链接一律用 `--brand-ink`(#0d6e66，白底 6.11:1)。
> 侧栏常态文字是 0.86 透明度的白（0.76 在渐变中段只有 4.38:1，不达标）。
> **改配色请同时重算对比度**，不要只凭观感调亮 —— 上一版就是把渐变调到亮青，
> 结果登录页副标题只有 2.14:1，几乎读不清。
>
> 状态色（正常/异常/警告）统一用 `--app-status-*` 令牌，不要在各页面裸写色值，
> 否则「工作台」和「系统设置」的正常色会不一致。
>
> 其中共享表单样式是**非 scoped** 的（表单由父组件插槽传入，scoped 样式碰不到插槽内容），
> 所以类名一律带 `auth-` / `field-` 前缀避免全局污染 —— 这是有意为之，不是漏写 scoped。

---

## 6. 代码约定

### 后端

- **分层职责**：Controller 不写业务逻辑、不直接调 Mapper；Service 不感知 HTTP。
- **统一响应**：所有接口返回 `ApiResponse<T>`。失败时**抛 `BizException`**，
  由 `GlobalExceptionHandler` 统一转换 —— 不要在 Controller 里手写 `try/catch` 拼错误返回。
- **入参校验**用 Jakarta Validation 注解（`@NotBlank`、`@Min` 等）加 `@Valid`，
  校验信息写在注解的 `message` 里。
- **请求体与实体分离**：新增/修改用独立的 `XxxForm`，不要直接拿 `@RequestBody` 接实体，
  否则客户端可能改到 `id`、`createdAt` 这类不该由它决定的字段。
- **时间字段**由数据库维护（`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`），
  实体上不加填充注解。MyBatis-Plus 默认的 `NOT_NULL` 插入策略会把 null 字段排除在 INSERT 外，
  数据库默认值因此生效。
  另外实体上必须给 `createdAt` / `updatedAt` 标
  `@TableField(insertStrategy = NEVER, updateStrategy = NEVER)`：
  不标的话，`updateById` 会把从库里读出来的**旧 `updated_at` 显式写回 SET 子句**，
  反而把数据库的 `ON UPDATE` 压掉 —— 表现是「数据改了但 `updated_at` 一直不变」（实测踩到）。
  这不是让应用接管时间，而是让应用**不去碰**它。
- **`updateById` 无法把字段改成 null**（实测踩到，且很容易忽略）。
  默认 NOT_NULL 策略会把 null 字段排除在 SET 之外，于是「把昵称清空」变成「不修改昵称」，
  用户点清空后刷新值还在。需要「可清空」语义时用显式 set 的更新包装器：
  `Wrappers.<User>lambdaUpdate().eq(...).set(User::getNickname, null)`（见 `UserService.overwriteEditable`）。
  **语义要分清**：`nickname/email/bio/age` 是「不传就清空」，`password`/`role` 是「不传就不改」。
- **分页必须走 `IService.page()`**，前提是 `MybatisPlusConfig` 里注册了 `PaginationInnerInterceptor`。
  少了它不会报错，而是悄无声息地查全表 —— 改分页相关代码时务必确认插件还在。
- **逻辑删除**：`@TableLogic` 标注 `deleted`，`removeById` 只置 1。所有查询自动带 `deleted = 0`。
- 删除接口用 `removeById`，不要写物理 `DELETE`。

### 前端

- 所有请求走 `src/api/`，**组件里不直接调 axios**。
- `http.ts` 的响应拦截器已把 `code !== 0` 转成 rejected Promise，
  所以组件里只写 `try/catch`，不要再判断 `code`。
- `request<T>()` 返回的已经是剥掉外壳的业务数据。
- 接口类型定义写在 `src/api/` 下，与后端字段对应（只声明前端用得上的字段）。
- 跨页面共享的状态放 `src/stores/`（Pinia setup 写法）；只服务单个组件的状态留在组件内 `ref`。
- 路径别名 `@/` 指向 `src/`。
- TypeScript 开了 `strict` 与 `noUnusedLocals`：**未使用的 import/变量会导致 `pnpm build` 失败**。

### 命名

- Java：类 `UpperCamelCase`，方法/变量 `lowerCamelCase`，常量 `UPPER_SNAKE_CASE`。
- 数据库：表 `snake_case` 且带模块前缀（当前 `sys_`），字段 `snake_case`，
  主键统一 `id`，时间字段统一 `created_at` / `updated_at`。
- Vue 组件文件 `PascalCase.vue`，组合式函数 `useXxx.ts`。

---

## 7. API 约定

- 所有接口挂在 `/api` 下。
- 响应体恒为 `{ code, message, data }`，`code = 0` 为成功。
- 资源路径用复数名词（`/api/users`），动作用 HTTP 方法表达，不在路径里写动词。
- 分页参数统一 `current`（从 1 开始）与 `size`；分页返回体直接用 MyBatis-Plus 的 `Page`
  结构（`records` / `total` / `current` / `size`）。
- 业务错误码集中定义在 `common/ErrorCode.java`：`4000` 参数校验失败，`4004` 资源不存在，
  `4009` 资源冲突（用户名已占用），`4010` 未认证，`4030` 无权限，`4290` 请求过于频繁（登录锁定），`5000` 未预期异常。
  **新增业务码必须加到那个类里**，不要各处随手写数字 —— 分散着写迟早会撞码。
- 对外返回值一律用 `dto/` 下的 VO/Form，**不要把 `entity` 直接当返回值**：
  `User` 带着 `passwordHash`，图省事返回实体就等于把密码哈希发给整个内网。

### 认证接口

| 方法 | 路径 | 需登录 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 否 | 入参 `{username, password}`，成功返回 `{token, tokenType, expiresIn, user}` |
| POST | `/api/auth/register` | 否 | 入参 `{username, password, email?}`，**成功也返回令牌（注册即登录）** |
| GET | `/api/auth/me` | 是 | 返回当前令牌对应的用户（含 `role`/`nickname`/`bio`） |
| PUT | `/api/auth/profile` | 是 | 改**自己**的 `nickname`/`email`/`bio`/`age`。**路径无 id，身份只从令牌取** |
| PUT | `/api/auth/password` | 是 | 改自己的密码，必须传 `oldPassword` |
| GET | `/api/health` | 否 | 健康检查（白名单，供前端探测后端是否可达） |

除白名单（`/api/auth/login`、`/api/auth/register`、`/api/health`）外，`/api/**` 全部需要
`Authorization: Bearer <token>`。配置见 `config/WebMvcConfig.java`。

三个容易踩的点：

- 鉴权失败时**同时**返回 HTTP 401 与 body 里的 `code=4010`，不只是 HTTP 200 + 业务码。
  前端 axios 按 HTTP 状态决定是否跳登录页，只给 200 会让它以为请求成功。
- 登录失败（用户不存在 / 密码错误）返回**完全相同**的错误码与文案，且用户不存在时
  也会空跑一次 BCrypt 校验来对齐响应时间 —— 否则「文案一样但快慢不同」照样能枚举用户名。
  改登录逻辑时不要破坏这两点。
- **注册的用户名判重必须靠唯一索引兜底，不能只写「先查一次有没有」**。
  原因：`@TableLogic` 让查询自动带上 `deleted = 0`，所以「已被逻辑删除的同名用户」
  查不出来，但它仍然占着 `username` 的唯一索引（实测会抛 `Duplicate entry`）。
  只查重会让这种请求落到兜底分支报 5000。正确做法是
  查重（给出友好提示）+ 捕获 `DuplicateKeyException`（保证正确性），见
  `AuthServiceImpl.register`。`api/AuthFlowTest` 里有对应断言。

### 用户接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/users` | 分页查询。参数 `keyword`（**同时匹配用户名与邮箱**）、`current`、`size` |
| GET | `/api/users/stats` | 概览统计：用户总数 + 缓存命中/未命中/命中率 |
| GET | `/api/users/{id}` | 用户详情，**走 Redis 缓存** |
| POST | `/api/users` | 新增，`password` 必填 |
| PUT | `/api/users/{id}` | 修改，`password` 留空表示不改密码 |
| DELETE | `/api/users/{id}` | 逻辑删除（只置 `deleted = 1`） |

`keyword` 的邮箱匹配**没有索引**（`sys_user` 只有 username 唯一索引与 created_at 普通索引），
会走全表扫描。当前数据量可接受；量大了应给 email 建索引或改前缀匹配。

### 权限模型（重要）

**权限按端点声明，不按路径配置**。在 Controller 类或方法上标 `@RequireRole(UserRole.ADMIN)`
即可；没标注解的端点默认「只要登录」。实现在 `security/RoleInterceptor`：

- 注册在 `/api/**` 上，**但只对带注解的端点生效**；
- 方法上的注解**优先于**类上的（便于整组收紧、个别放开）；
- **顺序必须在 `AuthInterceptor` 之后** —— 后者写入 `CurrentUser`，前者读它判角色；
  顺序反了会把管理员也拦掉，`RoleInterceptor` 里对此有显式告警；
- 非管理员返回 HTTP 403 + `code=4030`（403 而非 401：身份有效，只是权限不够，
  返回 401 会让前端误跳登录页）。

> **为什么不再用路径硬编码**：老做法是在 `WebMvcConfig` 里写死 `/api/users/**` 挂管理员拦截器。
> 新增管理模块时，只写 Controller 不生效，必须记得回来加一行 —— 漏一次就是越权，
> 而这种漏在 review 里几乎看不出来。注解把权限声明放在端点旁边，读 Controller 就知道谁能访问。

个人中心的两个接口**不校验角色**（任何登录用户都能改自己），安全性由结构保证：
路径里没有 id，userId 只从令牌取，因此不存在「改别人的资料」这条路径。

**三道自我保护的规则，改代码时不要移除**：

1. **不能修改自己的角色** —— 否则唯一管理员把自己降级后就再没人能进用户管理（只能改库）。
2. **不能删除自己的账号** —— 删掉后当前令牌立即失效，等于把自己踢出系统。
3. **个人资料接口不收 role / username / password 字段** —— DTO 里根本没有这些字段，
   客户端塞进请求体也会被 Jackson 忽略。这是「不可能提权」的结构保证，
   比「收到后再判断要不要用」更难写错。`api/PermissionTest#cannotEscalateThroughProfile` 专门验这条。

**改密码失败返回 4000 而不是 4010**：前端 http 拦截器见到 4010 会清登录态并跳登录页，
用户只是打错一次旧密码却被登出，体验极差。

**已知限制**：改密码后，别处已签发的旧令牌在过期前仍然有效。
无状态 JWT 没有服务端会话可销，要做到即时失效需要 Redis 黑名单（独立需求）。

### 登录失败限制

连续登录失败达到 `app.security.login.max-attempts`（默认 5）后，该用户名被临时锁定
`lock-minutes`（默认 15）分钟，返回 `code=4290`。实现在 `cache/LoginAttemptCache`：

- key `my-app:login:fail:{username}`，**每次失败都刷新 TTL**（滑动窗口）；
- **登录成功即清零** —— 「连续失败」语义，否则用户正常登录过、隔几天错两次就被锁；
- **用户不存在也计数**：否则「有没有计数」本身就成了用户名枚举信号；
- **4290 而不是 4010**：前端见到 4010 会清登录态并跳登录页，
  而被限流的用户并没有登录态问题，混用会表现成「突然被登出」；
- **Redis 故障时 fail-open**：计数与锁定都包了 try/catch，Redis 挂了放行登录并打 WARN。
  缓存是加速与防护手段，不该成为单点故障。

> **已知取舍**：按用户名锁定意味着攻击者可以故意输错来锁死别人的账号（DoS）。
> 缓解是「锁定时间短 + 到期自动解锁 + 成功清零」。面向公网的产品建议再叠加
> IP+用户名双维度计数或验证码；本项目按「管理后台」定位先做到这一步。

---

## 8. 缓存约定（Redis）

**缓存什么**：按主键缓存 `UserVO`（**不是 `User` 实体** —— 实体带 `passwordHash`，
而 Redis 通常被当低安全等级存储，不能把密码哈希放进去）。

| 项 | 值 |
|---|---|
| key | `my-app:user:{id}` |
| value | `UserVO` 的 JSON（含 `@class`，人可读） |
| TTL | 10 分钟 |
| 读路径 | `GET /api/users/{id}`、`AuthInterceptor`（**每个受保护请求都走它**，收益最大） |
| 失效 | `UserController` 的 create / update / delete，以及 `AuthServiceImpl.register` |

**四条必须遵守的约定**：

1. **任何写用户的地方都要调 `userCache.evict(id)`。** 漏掉的后果不是「数据略旧」，
   而是 `AuthInterceptor` 从缓存读到已删除用户、继续放行其旧令牌 ——
   **这是权限漏洞，不是缓存脏读**。`api/ProfileTest#profileUpdateEvictsCache` 有对应断言。
2. **缓存必须容忍 Redis 故障。** `UserCache` 里所有 Redis 操作都包了 try/catch，
   失败只记 WARN 并回源数据库。Redis 挂了用户管理必须照常可用，已实测。
   新增缓存方法时不要把这层保护去掉。
3. **只按 id 缓存，不要缓存分页列表。** 列表 key 要拼关键字/页码/每页条数，
   组合会爆炸，且任一条用户变动就得整片失效，极易返回脏数据。
4. **`RedisConfig` 里的 `activateDefaultTyping` 不能删。** 不开启时 JSON 里没有
   `@class`，反序列化只能得到 `LinkedHashMap`，于是缓存**key 正常、TTL 正常、
   不报任何错，但永远命中不了**（命中率一直是 0%）。这类故障极难排查 ——
   `UserCache.get()` 现在会在类型不符时打 ERROR，看到了就说明这里被改坏了。

> 缓存统计（`/api/users/stats`）来自**进程内计数器，重启后端会归零**，
> 它不是持久化指标，用途是让人一眼看出缓存有没有真的在起作用。

---

## 9. 数据库约定

- 库名 `my_app`，字符集 `utf8mb4` / `utf8mb4_general_ci`（不要用 `utf8`，它存不了 emoji）。
- 引擎统一 InnoDB。
- 主键 `BIGINT AUTO_INCREMENT`。
- 所有表带 `created_at` / `updated_at`（`DATETIME`，由数据库维护）。
- 需要逻辑删除的表带 `deleted TINYINT NOT NULL DEFAULT 0`。
- **唯一索引与逻辑删除有冲突**：`sys_user.username` 是唯一索引，但删除只置 `deleted = 1`，
  于是「已删除的用户名」仍占位，同名新用户会插入失败。要真正允许重用用户名，
  需把唯一索引改成 `(username, deleted)` 组合索引，或改为物理删除。**改这块前先确认需求。**
- **表结构由 Flyway 管理**，脚本在 `backend/src/main/resources/db/migration/`，
  命名 `V<版本>__<说明>.sql`。当前 `V1__init.sql`（建表 + 种子）、`V2__profile_role.sql`。
- **绝不修改已发布的迁移脚本**：`validate-on-migrate: true` 会比对 checksum，
  改过的文件会让已有环境**启动失败**。要变更结构就新增 `V3__xxx.sql`。
  这条规则看起来麻烦，但它正是「大家库结构一致」的保证。
- **`baseline-version: 2` 的含义**（`application.yml` 里有详细注释）：
  空库不触发 baseline，V1/V2 正常执行；已有数据的库在 V2 打基线、跳过 V1/V2，
  之后 V3+ 正常执行。**两种场景都已实测**，改动这个值前先想清楚已有环境会怎样。
- 新增环境（CI、新同事）不需要手工建表：建好库、启动应用即可。
- 迁移脚本里不要写 `CREATE DATABASE` / `USE`：库由外部创建，Flyway 只负责表。
- `sys_user` 现有字段：`id` / `username`（登录标识，唯一）/ `nickname`（显示名）/
  `password_hash` / `role`（ADMIN/USER）/ `email` / `bio` / `age` / `deleted` / 时间戳。
  **`username` 与 `nickname` 是两件事**：前者用于登录，后者只用于展示，个人中心只让改后者。

---

## 10. 配置与密钥

- 数据库密码**不进版本库**。优先级：环境变量 `DB_PASSWORD` >
  `application-local.yml`（已在 `.gitignore` 里）。
- **JWT 签名密钥（`APP_JWT_SECRET`）**：优先级为环境变量 `APP_JWT_SECRET` /
  启动参数 `-Dapp.jwt.secret=...` > 代码内置的开发密钥（`JwtTokenProvider.DEV_SECRET`）。
  - 未配置时**能正常启动**（降低本地开发门槛），但会在启动日志里打一条 WARN。
    那条 WARN 不是噪音，是在提醒你此刻的令牌任何人都能伪造。
  - **生产环境必须**：设置 `APP_JWT_SECRET`（至少 32 字符，可用
    `openssl rand -base64 48` 生成），并把 `app.jwt.require-external-secret` 设为 `true`。
    设成 true 后若忘记配密钥，应用会**直接启动失败**，而不是悄悄用公开密钥签发令牌。
  - 密钥长度不足 32 字符时同样启动失败（jjwt 会拒绝弱密钥）。
  - 令牌有效期 `app.jwt.expire-minutes` 默认 120 分钟。调大要权衡：
    无状态 JWT 无法在服务端提前失效，有效期越长，令牌泄露后的危险窗口越大。
- `application.yml` 里的 `log-impl: StdOutImpl` 会把每条 SQL 打到控制台，
  **上线前必须删掉**。
- 前端只有 `VITE_` 前缀的变量会暴露给浏览器，因此**前端环境变量里不放任何密钥**。
- Redis 本机未设密码，`application.yml` 中刻意不写 `password`。
  部署到有密码的环境时必须补上，并改为从环境变量读取。

---

## 11. 已知环境坑（都是实际踩到过的）

1. **Maven 本地仓库曾有一批全为空字节的损坏 POM**（2026/10/12 一次中断的下载所致），
   会让 Spring Boot 3.5.16 项目直接无法构建。已修复，清单与脚本留在 `docs/`：
   `corrupt-maven-files.json`、`scan-corrupt-maven.js`、`repair-corrupt-maven.js`。
   若再次出现 `Non-parseable POM ... only whitespace content allowed before start tag`，
   就是同类问题，按 `docs/环境说明.md` 第 5 节的步骤扫描并清理后加 `-U` 重新构建。
   （辅助脚本刻意写成 Node 而非 PowerShell：不受执行策略约束，也不受代码页影响。）

2. **PowerShell 5.1 的 `Get-Content` 默认用系统 ANSI 代码页（本机 GBK）
   读取无 BOM 的 UTF-8 文件**，中文注释会变乱码，进而引发莫名其妙的语法错误。
   读脚本/文本一律显式加 `-Encoding UTF8`。

   **同一个坑还有两个变体，写验证脚本时必踩**：

   - **自己写的 `.ps1` 若含中文，必须存成带 BOM 的 UTF-8**，否则
     `powershell.exe -File xxx.ps1` 会按 GBK 解析，中文注释里的全角括号会让脚本
     报出与真实原因毫不相干的语法错误（`Array index expression is missing`）。
     写入时用 `[System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($true)))`。
     （本项目的接口验证已改用 JUnit 集成测试，见 `backend/src/test`；但只要你临时写
     `.ps1` 手工探接口，这条坑依然成立。）
   - **`Invoke-WebRequest` 在 PS 5.1 下读不到非 2xx 的响应体**：
     `$_.Exception.Response.GetResponseStream()` 返回空串，于是「401 时 body 里
     `code=4010`」这类关键断言会全部误判失败。**校验接口请一律用 `curl.exe`**
     （`-s -o 文件 -w '%{http_code}'`，body 从文件按 UTF-8 读），不要用
     `Invoke-WebRequest` / `Invoke-RestMethod`。

   > 这条是实测踩出来的：最早那版 PowerShell 验证脚本用 `Invoke-WebRequest`，
   > 报了 4 个假失败，换成 `curl.exe` 后 25 项全过。
   > 现在接口验证已迁到 JUnit（`backend/src/test`），不再受这个坑影响 ——
   > 但只要你写临时脚本手工探接口，仍然要注意。

3. **`.ps1` 可能因执行策略或文件封锁而无法直接运行**。两个办法：
   先 `Unblock-File`，或改用 `-ExecutionPolicy Bypass` / 以 ScriptBlock 执行。

4. **`pwsh` 不是命令**（只装了 Windows PowerShell 5.1）。需要新语法时用
   `powershell.exe`，或直接写成兼容 5.1 的脚本。

5. **`mvn test` 依赖真实基础设施**：测试是 `@SpringBootTest` 集成测试，
   会连 MySQL 与 Redis（并触发 Flyway 迁移）。**库不存在或没配密码时会失败**，
   这不是代码问题 —— 先建库、配好 `DB_PASSWORD`。只想验证能否打包就用
   `mvn -DskipTests package`。CI 里由 service 容器提供 MySQL 与 Redis。

6. **前端 `pnpm build` 会先跑 `vue-tsc`**。类型错误会阻断构建，
   而 `pnpm dev` 不会报同样的错 —— 提交前端改动前跑一次 `pnpm type-check`。

7. **4990–5188 整段 TCP 端口被 Hyper-V 预留**，Vite 默认的 5173 和回退的 5174 都在其中，
   绑定必然 `EACCES`。前端端口因此固定在 **3000**。查保留区间：

   ```powershell
   netsh interface ipv4 show excludedportrange protocol=tcp
   ```

   新增任何监听端口的服务前，先按上面命令确认端口不在保留区间内。

8. **PowerShell 5.1 的 `Invoke-RestMethod` 会把 `Content-Type: application/json`
   （不带 charset）的响应按 Latin-1 解码**，于是中文变成 `ç¨æ·` 这样的乱码。
   **后端返回的其实是正确的 UTF-8**（可用 `[System.Text.Encoding]::UTF8.GetString($bytes)`
   验证字节）。要正确显示中文，用 `WebClient` 显式设 `Encoding` 或自己解码字节。
   前端 axios 不受此影响。

9. **`pnpm install` 会提示 `Ignored build scripts: esbuild, vue-demi`。**
   本项目已在 `package.json` 的 `pnpm.onlyBuiltDependencies` 里声明允许这两个包构建。
   若看到该提示且 `pnpm dev`/`pnpm build` 正常，可忽略 —— esbuild 的二进制来自平台专用
   可选依赖，不依赖 postinstall 脚本。

10. **`java -jar target/xxx.jar` 会把 jar 锁住，导致之后 `mvn clean` 失败。**
    Windows 上正在运行的 JVM 独占该 jar 文件，清理时报：

    ```
    Failed to delete ...\target\my-app-0.0.1-SNAPSHOT.jar:
    另一个程序正在使用此文件，进程无法访问。
    ```

    **这不是依赖问题**，就是有个旧进程还在跑。排查与处理：

    ```powershell
    Get-Process java,node -ErrorAction SilentlyContinue | Select-Object Id,ProcessName
    Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    ```

    确认后结束那个进程即可。**日常启动后端优先用 `mvn spring-boot:run`，
    或在 IDEA 里用 Spring Boot 运行配置** —— 它们不占用 `target` 下的成品 jar，
    也就不会和 `clean` / `package` 打架。真要跑成品 jar，先把服务停掉再构建。

11. **IDEA 里用 Lombok 需要开启注解处理。** 本项目实体与 DTO 用 `@Data` / `@Slf4j` 等
    注解在编译期生成 getter/setter。命令行 Maven 构建不受影响，但 IDEA 需要：
    `Settings → Build, Execution, Deployment → Compiler → Annotation Processors`
    勾选 `Enable annotation processing`；同时确认已启用 Lombok 插件。
    否则编辑器会满屏 `cannot resolve method getUsername()` 之类的红色报错，
    而 `mvn compile` 却是绿的 —— 那是编辑器索引问题，不是代码问题。

12. **IDEA 2024.1 与 Maven 3.10.0 不兼容。** 症状很有迷惑性：
    **IDEA 里没有依赖树、所有 Java 文件爆红，但命令行 `mvn` 一切正常。**
    真因在 `%LOCALAPPDATA%\JetBrains\IntelliJIdea<版本>\log\idea.log`：

    ```
    SEVERE - #o.j.i.maven - com.google.inject.CreationException: Unable to create injector
    [Guice/MissingImplementation]: No implementation for ArtifactHandlerManager was bound.
    Requested by: DefaultArtifactFactory.artifactHandlerManager
       at MavenProjectResolver.resolveProjectsInEmbedder
    ```

    Maven 3.10.0 把 Maven Resolver 从 **1.9.18 升到 2.0.24**，依赖注入的装配方式随之改变；
    IDEA 2024.1 的嵌入容器是按 Resolver 1.x 构建的，于是导入进程一启动就崩，
    模块建不出来（`.idea/*.iml` 只剩空壳），classpath 为空 → 全文件爆红。

    **二选一：**

    - **让 IDEA 用自带的 Maven**：`Settings → Build, Execution, Deployment →
      Build Tools → Maven → Maven home path` 改成 **`Bundled (Maven 3)`**
      （IDEA 2024.1 自带 3.9.6，位于 `plugins\maven\lib\maven3`）。
      命令行不受影响，仍可继续用 3.10.0。
    - **升级 IDEA** 到 2025.3 或更高（2025.3 起改为统一发行版，已支持 Spring Boot 4 /
      Framework 7）。2024.1 到新版本没有增量补丁路径，需要下载完整安装包。

    **两种做法都要顺手核对这一项**：`Settings → Maven → Local repository` 必须是
    `F:\JavaTool\Maven\maven_repository`。若它回落成 `C:\Users\FanLian\.m2\repository`，
    Spring Boot 全套依赖会被重新下载几百 MB，本机还会出现两份缓存。
    镜像与本地仓库的配置参考见 `docs/maven-settings-参考.xml`。

    排查通用思路：**IDEA 里报依赖问题而命令行正常时，先看 `idea.log`**，
    真实异常几乎总在里面，比 IDEA 前台的笼统提示有用得多。

---

## 12. 改动前必做（给 AI agent 的工作约定）

### 每次改完代码，按下表验证

| 改了什么 | 至少跑什么 |
|---|---|
| 后端 Java 代码 | `mvn -q compile`；涉及逻辑或 SQL 时再跑 `mvn test` |
| 后端 pom.xml / 依赖 | `mvn -DskipTests package`（依赖解析问题只有打包才会暴露） |
| 前端 TS / Vue | `cd frontend; pnpm type-check` |
| 前端依赖或构建配置 | `pnpm build` |
| 数据库结构（`db/migration/V*.sql`） | 起一次应用看 `flyway_schema_history`；空库与已有库两种场景都要过 |
| API 契约（路径/字段/错误码） | 同步更新后端 DTO、前端 `src/api/`、以及本文件的接口约定 |
| 权限相关（`@RequireRole`） | `mvn test`，`PermissionTest` 必须全过 —— 权限回归不会报错，只会静默越权 |
| 任何改动 | 推上去看 CI（`.github/workflows/ci.yml`）：后端测试 + 前端 type-check/build |

### 硬性约束

- **不要为了「让构建通过」而放宽校验**：不要删 `@Valid`、不要把 `strict` 改成 `false`、
  不要加 `@SuppressWarnings` 掩盖问题。要么修根因，要么说明为什么当前约束不合理。
- **不要顺手升级框架大版本**（Spring Boot 4、Vite 7 等）。升级是独立任务。
- **不要在实体上加 `@TableField(fill=...)`** 来接管时间字段，除非同时提供 `MetaObjectHandler`
  并说明理由 —— 当前设计让数据库负责时间。
- **不要把分页改成「查全表再切页」**，也不要在移除分页插件的情况下改分页代码。
- **不要提交密钥**。新增配置项一律走环境变量，并在第 10 节登记。
- **改数据库结构时新增一个 `V<n>__xxx.sql` 迁移**（不要改已发布的迁移脚本），
  并在提交说明里写清影响与回滚方式。
- **不要把权限校验从注解挪回路径配置**，也不要用「前端隐藏了入口」当作权限保证。
- 新增接口后更新第 7 节；新增环境相关坑后更新第 11 节。本文件随代码一起演进。

### 提交信息

用「`模块: 做了什么`」的形式，例如 `backend: 新增用户批量导入接口`、
`frontend: 修正分页切换时的重复请求`。一个提交只做一件事。
