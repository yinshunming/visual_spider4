# 运维手册

## 启动顺序

```
1. 本机 PostgreSQL 服务（端口 5432）
       ↓
2. Backend Spring Boot（端口 8080）
       ↓
3. Frontend Vite（端口 5173，代理 /api → 8080）
```

## PostgreSQL

### 安装（一次性，按 OS 选择）

| OS | 安装方式 |
|----|---------|
| **Windows** | 下载 [EDB PostgreSQL 16 installer](https://www.enterprisedb.com/downloads/postgres-postgresql-downloads)，安装时设置密码为 `123456`，自动注册为 `postgresql-x64-16` Windows 服务 |
| **macOS** | `brew install postgresql@16` |
| **Linux (Debian/Ubuntu)** | `sudo apt install postgresql-16` |
| **Linux (RHEL/Fedora)** | `sudo dnf install postgresql-server` |

### 启动本机 PG 服务

| OS | 启动命令 |
|----|---------|
| **Windows** | 在"服务"中找到 `postgresql-x64-16` → 右键"启动"；或 `net start postgresql-x64-16`（管理员 PowerShell） |
| **macOS** | `brew services start postgresql@16` |
| **Linux (systemd)** | `sudo systemctl start postgresql` |

> ⚠️ **本项目不再使用 Docker 启动 PG**。`docker-compose.yml` 已删除，相关 docker 启动方式（`docker compose up -d`）已废弃。

### 验证

```bash
pg_isready -h localhost -p 5432
# 期望：localhost:5432 - accepting connections
```

或用 `psql` 客户端：
```bash
psql -h localhost -U postgres -c "SELECT version();"
```

### 表结构

dev 模式 `application.yml` 设 `ddl-auto: update`，启动时自动建表/更新表。
test 模式 `application-test.yml` 设 `ddl-auto: create-drop`，测试前后建/删表。

> **page-fetch 配置项**：`application.yml` 中有 `page-fetch.*` 块（`timeout` / `max-size` / `user-agent`），默认值见文件注释。修改后重启后端生效。

> ⚠️ **生产环境不要用 ddl-auto**。M2+ 应引入 Flyway / Liquibase 管理 migration。

### 创建库

如果 `visual_spider4`（主库）或 `visual_spider4_test`（测试库）不存在：

```bash
# 一次性创建主库
psql -U postgres -c "CREATE DATABASE visual_spider4;"

# 一次性创建测试库
psql -U postgres -c "CREATE DATABASE visual_spider4_test;"
```

> Windows 上若 `psql` 不在 PATH，先用 EDB 安装器自带的 "SQL Shell (psql)" 登录，或把 `C:\Program Files\PostgreSQL\16\bin` 加到 PATH。

## Backend

### 启动（dev）

```bash
cd backend
mvn spring-boot:run
```

启动后：
- 监听 8080
- 健康检查：`http://localhost:8080/api/v1/health`
- 首次启动自动建表（`ddl-auto: update`）
- **如果 PG 未启动**：启动日志会打印多行 ASCII banner（关键字"PostgreSQL 未启动"），`/api/v1/health` 返回 `database: DOWN` + `message` 含明确启动指引（见下文"Agent 协作契约"）

> ⚠️ **Lombok 增量编译陷阱**：`mvn spring-boot:run` 启动时只会增量编译修改过的源文件。如果 `target/classes/` 里残留了上一次失败编译的、**没经过 Lombok 处理的** `.class` 文件（如之前 `mvn compile` 报错后中断留下的），这次 `mvn spring-boot:run` 可能不重新编译它们，导致启动时报 `Unresolved compilation problems` / `The blank final field xxx may not have been initialized` / `The method setXxx is undefined for the type Xxx`。
>
> **解决方法**（出现上述错误时执行）：
> ```bash
> mvn clean compile
> mvn spring-boot:run
> ```
>
> 或一次性用打包好的 jar（推荐，绕过增量编译）：
> ```bash
> mvn clean package -DskipTests
> java -jar target/visual-spider-backend-0.0.1-SNAPSHOT.jar
> ```

### 跑测试

```bash
cd backend
DB_NAME=visual_spider4_test mvn test       # 跑所有测试（详见 docs/tdd-guide.md §当前测试统计；M1 44 + M2 26 + M2.5 31 + M4 ~50+）
DB_NAME=visual_spider4_test mvn test -Dtest=PageFetchServiceTest   # 单跑某类
DB_NAME=visual_spider4_test mvn test -Dtest='*ServiceTest'         # 按通配符
```

Windows PowerShell 等价：`$env:DB_NAME='visual_spider4_test'; mvn test`

> 跑测试前**必须**先启动本机 PG 并创建 `visual_spider4_test` 库（见上文"创建库"）。
> **`DB_NAME=visual_spider4_test` 不可省** —— `application-test.yml` 只覆盖 JPA 设置、不覆盖数据源，缺省值指向开发库 `visual_spider4`；测试配置 `ddl-auto: create-drop` 会在开发库建/删表，清空爬取数据。

测试结果按 `@Nested` 分组输出（如 `CrawlConfigServiceTest$Create`）。

### 编译

```bash
cd backend
mvn clean compile          # 编译 main
mvn clean test-compile     # 编译 main + test
mvn clean package -DskipTests  # 打 jar（跳过测试）
```

### 日志

默认 `INFO` 级别，包 `com.visualspider`。JPA SQL 静默（`show-sql: false`）。
调试 SQL 时改为 `true` 或在启动参数加 `--logging.level.org.hibernate.SQL=DEBUG`。

## Frontend

### 启动（dev）

```bash
cd frontend
npm install  # 首次
npm run dev
```

启动后：
- 监听 5173
- 已配代理：`/api` → `http://localhost:8080`
- HMR 启用，修改 Vue/JS 实时刷新

### 跑测试

```bash
cd frontend
npm test         # vitest run（一次性）
npm run test:watch  # 监听模式
```

> **注意**：M1 前端组件未写 vitest 测试，仅配置了测试框架。M2 起补齐 store / 组件测试（pageFetchStore 4 + PagePreview 4 = 8 项）。

### 打包

```bash
cd frontend
npm run build    # 输出到 dist/
npm run preview  # 本地预览构建产物
```

## 端到端联调验证

```bash
# 1. 后端起来
curl http://localhost:8080/api/v1/health
# {"code":200,"data":{"status":"UP","database":"UP","message":null,"timestamp":"..."},"message":"success"}

# 2. 前端起来后，浏览器打开
open http://localhost:5173

# 3. 创建配置（M4 起 startUrl 必填）
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"name":"测试","startUrl":"https://example.com/list","pageType":"LIST_DETAIL","selectorType":"CSS"}'

# 4. 列出
curl http://localhost:8080/api/v1/configs

# 5. M4 创建任务（LIST_DETAIL）
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"configId":1,"urls":null}'

# 6. M4 查看任务与该任务下的 article
curl http://localhost:8080/api/v1/tasks/1
curl 'http://localhost:8080/api/v1/articles?task_id=1&page=0&size=20'
```

## Agent 协作契约

后端 `HealthService` 在数据库不可用时返回结构化错误信号（`/api/v1/health` 响应体 `data.message` 字段以 `PG_NOT_READY: ` 开头）。开发期 AI agent 应**主动识别**该信号并在当前会话直接告知用户手工启动本机 PG。

| 触发场景 | `data.message` 内容 | Agent 行为 |
|---------|---------------------|-----------|
| PG 未启动 | `PG_NOT_READY: PostgreSQL 未启动，请手工启动本机服务（默认端口 5432）...` | 直接告诉用户"PG 没起，请启一下" |
| 数据库不存在 | `PG_NOT_READY: 数据库 visual_spider4 不存在，请先 \`psql -U postgres -c 'CREATE DATABASE visual_spider4;'\`` | 告诉用户并给出建库命令 |
| 密码错误 | `PG_NOT_READY: PostgreSQL 密码错误（默认期望 postgres/123456）...` | 告诉用户并提示设置环境变量 |
| 其他 | `PG_NOT_READY: 数据库连接失败：<原因>，详见 docs/runbook.md。` | 告诉用户并指向本手册 |

后端启动日志侧也会打印多行 ASCII banner（`===` 边框 + 关键字"PostgreSQL 未启动"），agent 看日志也能识别。

## Playwright 启动

M2 起的可视化页面预览功能依赖 Playwright 内嵌 Chromium。首次启动前需先安装 Chromium：

```bash
# 在 backend 目录下
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

Windows 上 PowerShell 执行同上；首次下载约 130MB，启动时再解压到 `~/.cache/ms-playwright/`。

> **如果 Chromium 未安装**：`BrowserSessionService` 在 `@PostConstruct` 阶段会捕获异常并打印多行 banner（参考 `docs/architecture.md` 中 PG 启动 banner 风格），`PlaywrightConfig` Bean 降级返回 `null`，后端其它 REST 仍可访问；`POST /api/v1/browser/sessions` 与 `POST /api/v1/ws/page` 会因 Playwright 不可用返回 503 / error。

### Chromium 进程残留处理

JVM 异常退出时偶有 `chrome.exe` 残留。Windows 上检查：

```powershell
tasklist /FI "IMAGENAME eq chrome.exe"
```

如有残留，`taskkill /F /IM chrome.exe` 强杀。

## Known Issues

### 1. 端口/进程残留导致 E2E 启动失败

**症状**：`cd e2e && npm test` 报 `EADDRINUSE: address already in use :::8080` 或 `EADDRINUSE: 5173`，或后台 backend 启了两个互相争端口。

**原因**：上一轮 `npm test` 异常退出时残留 `java.exe` / `chrome.exe` / `node.exe` 进程没被回收；或手工跑 jar 时漏 `Stop-Process`。

**解决**（Windows PowerShell）：
```powershell
# 强杀所有残留
Get-Process -Name java,chrome,node -ErrorAction SilentlyContinue | Stop-Process -Force
# 验证端口空闲
Get-NetTCPConnection -LocalPort 8080,5173 -ErrorAction SilentlyContinue
# 重跑
cd e2e; npm test
```

### 2. 集成测试里 `npm test` 工具进程被"主动 kill"

**症状**：agent 工具跑 `npm test`（含 Playwright + Chromium）时报 `Unknown: ChildProcess.kill`，测试在 60s 之前被中断。

**原因**：某些 agent 的 bash 工具对长任务 + 子进程（Playwright spawn Chrome）的容忍度低，主动杀进程。

**解决**：用 `Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','cd /d D:\opencodeSpace\visual_spider4\e2e && npm test > run.log 2>&1' -PassThru -WindowStyle Hidden` 后台跑，agent 用 `Get-Content run.log` 轮询日志判断进度。

### 3. Playwright 报 `Unsupported type of argument: [I@xxx`

**症状**：`page.evaluate(script, arg)` 抛 `PlaywrightException: Unsupported type of argument`。

**原因**：Java `int[]` / 数组不能直接当 JS 数组传给 `page.evaluate`。

**解决**：改用 `Map.of("x", ..., "y", ...)` 或 `Object[]`，让 Playwright 用 Jackson 序列化为 JSON 对象。

### 4. Jackson 反序列化枚举大小写敏感

**症状**：前端发 `selectorType: "css"`，后端 `mapper.convertValue` 抛 `IllegalArgumentException: not one of the values accepted for Enum class: [CSS, XPATH]`。

**原因**：Jackson 默认 case-sensitive，enum value `CSS` 拒绝小写 `"css"`。

**解决**：构造 `ObjectMapper` 时 `configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)`（已在 `PageWebSocketHandler` 配）。

## 故障排查

### 后端启动失败：`Communications link failure` 或 banner 提示 PG 未启动

数据库没起来、端口错、或者连接参数错。
1. `pg_isready -h localhost -p 5432` 看 PG 是否运行
2. 检查 `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` 环境变量
3. 测连通性：`psql -h localhost -U postgres -d visual_spider4`
4. 查 `/api/v1/health` 响应的 `data.message` 字段（结构化错误信号）

### 前端启动失败：`Cannot find module 'vue-router'`

依赖没装：`cd frontend && npm install`

### 测试报 `FATAL: database "visual_spider4_test" does not exist`

测试库未创建：`psql -U postgres -c "CREATE DATABASE visual_spider4_test;"`

### 前端代理不生效：前端 404

后端没起。`curl http://localhost:8080/api/v1/health` 验证。

## 端口冲突

| 端口 | 占用 | 处理 |
|------|------|------|
| 8080 | 其他 Java 应用 | `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081"` |
| 5173 | 其他 Vite | `npm run dev -- --port 5174` |
| 5432 | 其他 PG | 修改本机 PG 端口（`postgresql.conf` 的 `port`，改完 `sudo systemctl restart postgresql`）或调整 `DB_PORT` 环境变量 |
