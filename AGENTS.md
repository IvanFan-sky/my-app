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

**为什么 Spring Boot 停留在 3.5.x 而不是 4.x**：3.5 是 3.x 最后一条线，中文生态
（MyBatis-Plus、Druid、Hutool 等）与绝大多数教程都对齐它；4.x 需要 Spring Framework 7，
部分三方库尚未跟上。升级 4.x 是独立任务，需单独评估并跑通全部测试，不要顺手升。

**为什么用 MyBatis-Plus 而不是 JPA**：本项目定位是国内主流业务后端，MyBatis-Plus 的
单表 CRUD + 条件构造器更贴近实际团队习惯，且 SQL 可控。

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
# 建库建表 + 种子数据（⚠️ 会 DROP 掉 sys_user）
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p < sql\init.sql

# 进客户端
& 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p my_app
```

### Redis

```powershell
& 'F:\JavaTool\Redis-x64-8.10.2\redis-cli.exe' ping    # 期望 PONG
```

---

## 5. 目录结构

```
backend/src/main/java/com/example/myapp/
├── MyAppApplication.java   # 启动类，@MapperScan("com.example.myapp.mapper")
├── common/                 # ApiResponse（统一响应）、BizException、GlobalExceptionHandler
├── config/                 # MybatisPlusConfig（分页插件必须在此注册）
├── controller/             # 只做参数校验与编排，不写业务逻辑
├── entity/                 # 与数据库表一一对应
├── mapper/                 # 继承 BaseMapper，复杂 SQL 放 resources/mapper/*.xml
└── service/ + service/impl # 业务逻辑，继承 IService / ServiceImpl

frontend/src/
├── api/          # http.ts（axios 实例 + 拦截器）、user.ts（接口定义）
├── router/       # 路由表
├── stores/       # Pinia store（setup 写法）
├── views/        # 页面组件，一页一文件
├── App.vue       # 根布局，不放业务逻辑
└── main.ts       # 应用装配
```

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
- 业务错误码约定：`4000` 参数校验失败，`4004` 资源不存在，`5000` 未预期异常。
  新增业务码请在此处登记，不要各处随手写数字。

---

## 8. 数据库约定

- 库名 `my_app`，字符集 `utf8mb4` / `utf8mb4_general_ci`（不要用 `utf8`，它存不了 emoji）。
- 引擎统一 InnoDB。
- 主键 `BIGINT AUTO_INCREMENT`。
- 所有表带 `created_at` / `updated_at`（`DATETIME`，由数据库维护）。
- 需要逻辑删除的表带 `deleted TINYINT NOT NULL DEFAULT 0`。
- **唯一索引与逻辑删除有冲突**：`sys_user.username` 是唯一索引，但删除只置 `deleted = 1`，
  于是「已删除的用户名」仍占位，同名新用户会插入失败。要真正允许重用用户名，
  需把唯一索引改成 `(username, deleted)` 组合索引，或改为物理删除。**改这块前先确认需求。**
- 表结构变更目前靠手改 `sql/init.sql`。**表变多或开始多人协作后，应切换到 Flyway / Liquibase**，
  别继续用「改 init.sql + 手动执行」的方式。

---

## 9. 配置与密钥

- 数据库密码**不进版本库**。优先级：环境变量 `DB_PASSWORD` >
  `application-local.yml`（已在 `.gitignore` 里）。
- `application.yml` 里的 `log-impl: StdOutImpl` 会把每条 SQL 打到控制台，
  **上线前必须删掉**。
- 前端只有 `VITE_` 前缀的变量会暴露给浏览器，因此**前端环境变量里不放任何密钥**。
- Redis 本机未设密码，`application.yml` 中刻意不写 `password`。
  部署到有密码的环境时必须补上，并改为从环境变量读取。

---

## 10. 已知环境坑（都是实际踩到过的）

1. **Maven 本地仓库曾有一批全为空字节的损坏 POM**（2026/10/12 一次中断的下载所致），
   会让 Spring Boot 3.5.16 项目直接无法构建。已修复，清单与脚本留在 `docs/`：
   `corrupt-maven-files.json`、`scan-corrupt-maven.js`、`repair-corrupt-maven.js`。
   若再次出现 `Non-parseable POM ... only whitespace content allowed before start tag`，
   就是同类问题，按 `docs/环境说明.md` 第 5 节的步骤扫描并清理后加 `-U` 重新构建。
   （辅助脚本刻意写成 Node 而非 PowerShell：不受执行策略约束，也不受代码页影响。）

2. **PowerShell 5.1 的 `Get-Content` 默认用系统 ANSI 代码页（本机 GBK）
   读取无 BOM 的 UTF-8 文件**，中文注释会变乱码，进而引发莫名其妙的语法错误。
   读脚本/文本一律显式加 `-Encoding UTF8`。

3. **`.ps1` 可能因执行策略或文件封锁而无法直接运行**。两个办法：
   先 `Unblock-File`，或改用 `-ExecutionPolicy Bypass` / 以 ScriptBlock 执行。

4. **`pwsh` 不是命令**（只装了 Windows PowerShell 5.1）。需要新语法时用
   `powershell.exe`，或直接写成兼容 5.1 的脚本。

5. **`mvn test` 依赖真实基础设施**：`MyAppApplicationTests` 是 `@SpringBootTest`，
   会连 MySQL 与 Redis。**没建库或没配密码时它会失败**，这不是代码问题。
   只想验证能否打包就用 `mvn -DskipTests package`。

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

## 11. 改动前必做（给 AI agent 的工作约定）

### 每次改完代码，按下表验证

| 改了什么 | 至少跑什么 |
|---|---|
| 后端 Java 代码 | `mvn -q compile`；涉及逻辑或 SQL 时再跑 `mvn test` |
| 后端 pom.xml / 依赖 | `mvn -DskipTests package`（依赖解析问题只有打包才会暴露） |
| 前端 TS / Vue | `cd frontend; pnpm type-check` |
| 前端依赖或构建配置 | `pnpm build` |
| `sql/init.sql` | 在事务或临时库里实际执行一遍，确认能建起来 |
| API 契约（路径/字段/错误码） | 同步更新后端 DTO、前端 `src/api/`、以及本文件的接口约定 |

### 硬性约束

- **不要为了「让构建通过」而放宽校验**：不要删 `@Valid`、不要把 `strict` 改成 `false`、
  不要加 `@SuppressWarnings` 掩盖问题。要么修根因，要么说明为什么当前约束不合理。
- **不要顺手升级框架大版本**（Spring Boot 4、Vite 7 等）。升级是独立任务。
- **不要在实体上加 `@TableField(fill=...)`** 来接管时间字段，除非同时提供 `MetaObjectHandler`
  并说明理由 —— 当前设计让数据库负责时间。
- **不要把分页改成「查全表再切页」**，也不要在移除分页插件的情况下改分页代码。
- **不要提交密钥**。新增配置项一律走环境变量，并在第 9 节登记。
- **改数据库结构时同步更新 `sql/init.sql`**，并在提交说明里写清影响。
- 新增接口后更新第 7 节；新增环境相关坑后更新第 10 节。本文件随代码一起演进。

### 提交信息

用「`模块: 做了什么`」的形式，例如 `backend: 新增用户批量导入接口`、
`frontend: 修正分页切换时的重复请求`。一个提交只做一件事。
