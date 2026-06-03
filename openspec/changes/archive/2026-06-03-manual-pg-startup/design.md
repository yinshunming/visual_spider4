## Context

### 现状
- `docker-compose.yml` 仍是仓库顶层文件，定义 `visual_spider4_postgres` 容器（`postgres:16-alpine`，账号 `postgres / 123456`，库 `visual_spider4`）。
- `backend/src/main/resources/application.yml` 已用 `${DB_HOST:localhost}:${DB_PORT:5432}` 默认指向本机 PG——**配置层已经支持纯手工启动**，无需改 datasource。
- 集成测试 `CrawlConfigRepositoryTest` / `CrawlFieldRepositoryTest` 已走 `@ActiveProfiles("test")` + `application-test.yml`，连本机 `visual_spider4_test` 库。`IntegrationTestBase.java`（Testcontainers 基类）已无任何测试继承，是死代码。
- `HealthService.checkDatabase()` 用 `dataSource.getConnection().isValid(1)` 检测连通性，catch 块只 log `e` 并返 `"DOWN"`——错误信息对人和 agent 都不友好。
- 项目**无 CI**（无 `.github/workflows/`）、**无 `.env` 文件**、**无 Makefile/Taskfile**、**无 shell 脚本**——所有"启动"指令都写在 Markdown 文档里。

### 约束
- 不引入新依赖（如 Liquibase/Flyway）——`runbook.md` 标注为 M2+ 工作。
- `application.yml` 的 env-var-with-default 模式保留（不删默认值）。
- 归档目录 `openspec/changes/archive/2026-05-31-establish-visual-crawler-baseline/*` 不改。
- 集成测试必须用本机 PG，不能用 docker 容器（用户明确决策 A）。
- 项目 Lombok 已用（`@Slf4j` 等），HealthService 重写后继续走 record + `@Slf4j`。

### 利益相关方
1. **开发者**——不再需要 Docker Desktop；`mvn test` / `mvn spring-boot:run` 要求本机 PG 跑在 `localhost:5432`。
2. **AI agent（开发期）**——执行需要 PG 的命令前后，agent SHALL 通过 `/api/v1/health` 探测 PG 状态；PG DOWN 时 SHALL 在当前会话直接告知用户手工启动。
3. **未来 review/审阅**——OpenSpec spec 与实现必须一致，否则 AGENTS.md §3 的 capability 状态表会失真。

## Goals / Non-Goals

**Goals:**
- 删除 `docker-compose.yml`、清理 Testcontainers 死代码与死依赖。
- 文档（README/AGENTS/runbook/tdd-guide）改写：所有"docker compose up"指令替换为"启动本机 PG 服务"指引。
- `HealthService` 在 PG 不可用时返回**结构化、agent 可识别**的错误信号：HTTP 200 响应体里 `database: "DOWN"` + `message` 含"PostgreSQL 未启动"等可读中文文本。
- 启动日志在 PG 检测失败时打印醒目的多行 ASCII banner，便于用户在终端一眼看到。
- 同步改 `openspec/specs/dev-environment/spec.md` 与 `openspec/changes/m1-project-management/*` 三份活跃文档。
- 37 项 mvn test 仍通过。

**Non-Goals:**
- 不做生产级数据库迁移（Flyway/Liquibase）。
- 不改 API 行为（`/api/v1/health` 端点、响应包络 `ApiResponse<T>`、HTTP 状态码语义保留）。
- 不动前端（frontend/ 零 docker 引用，无需改）。
- 不写新的"启动脚本"或"启动文档章节以外的程序"——agent 协作面靠契约信号 + banner，不靠额外组件。
- 不动归档目录 4 个文件。

## Decisions

### D1. 删除 `docker-compose.yml`（而非保留为可选项）
**选择**：物理删除文件。
**理由**：用户决策 A；保留 `.example` 形式与"开发不再用 docker"语义矛盾，容易让新人误以为可选。
**替代方案考虑**：
- 保留 + deprecation 注释：分裂文档、与"唯一方式"语义冲突。
- 改名为 `docker-compose.yml.example`：仍是 docker 痕迹，删根目录最干净。

### D2. HealthResponse 加 `message` 字段
**选择**：`record HealthResponse(String status, String database, String timestamp, String message)`，`message` UP 时为 `null`，DOWN 时含可读文本。
**理由**：现有字段已够识别 UP/DOWN 二元状态，但 agent 需要更明确的"为什么 DOWN"语义来判断是否提示用户启动 PG。新字段是 `null`able，向后兼容。
**替代方案考虑**：
- 加 `errorCode` 枚举字段：与"需要自然语言提示"目标不匹配，agent 仍要自己拼文案。
- 不加字段、改用 `database` 字段塞 JSON：破坏现有 `database: "UP"/"DOWN"` 契约，违反 AGENTS.md 红线（"保持 ApiResponse 包络"）。
- 加 `details: Map<String, Object>`：过度设计，单一可读字符串足够。

### D3. HealthService 用 `instanceof` 模式匹配识别连接失败
**选择**：catch 块先判 `e instanceof java.sql.SQLException sqlEx`，再判 `sqlEx.getMessage().contains("Connection refused")` 或 `contains("FATAL: database")` 等模式，分别映射为不同 `message`。
**理由**：
- `Connection refused` → "PostgreSQL 未启动，请手工启动本机服务（默认端口 5432）"。
- `FATAL: database "X" does not exist` → "数据库 visual_spider4 不存在，请先 `psql -U postgres -c 'CREATE DATABASE visual_spider4;'`"。
- 其他 SQLException → 通用 "数据库连接失败：{原因摘要}，参见 docs/runbook.md"。
**替代方案考虑**：
- 直接暴露 `e.getMessage()`：可能含 stack trace 或敏感路径（违反 `java/security.md`）；也不友好。
- 引入 `ErrorDecoder`/Spring `@ControllerAdvice` 统一处理：超出本 change 范围（health 是主动探测，不是被动异常）。

### D4. 启动日志用 ASCII banner 风格
**选择**：当 `checkDatabase()` 返 DOWN 时，`log.error` 用 `===` 边框打印多行 banner：

```
================================================================
  PostgreSQL 未启动！
  请手工启动本机 PostgreSQL 服务（默认端口 5432）
  库: visual_spider4   用户: postgres   密码: 123456
  启动后请告诉我继续。
  详细排错：docs/runbook.md
================================================================
```

**理由**：终端显眼，agent 抓日志时也好定位（关键字"PostgreSQL 未启动"）。
**替代方案考虑**：
- 用 ANSI 颜色：跨平台/IDE 终端兼容差，Windows PowerShell 默认色不友好。
- 只 log 一行：与项目其他重要错误风格不一致，混在 info 日志里容易被忽略。

### D5. 不引入新工具，直接靠 agent 识别 + 契约信号
**选择**：不动程序侧（不加 health-check 定时任务、不加 WebSocket 推送），靠 agent 在执行 `mvn spring-boot:run` / `mvn test` / `GET /api/v1/health` 前后自己判断。
**理由**：用户决策 4（"agent 在会话里直接告诉我"）明确指向 agent 行为而非程序行为。HealthService 返回的 `message` + 启动 banner 是 agent 识别的**契约信号**——agent 见到含"未启动"字样就直接对话告知。
**替代方案考虑**：
- 后端启动时直接拒绝启动、抛 `BeanCreationException`：硬，但失去"我想自己起后端看日志"的可能。
- 加定时心跳检查：超出 scope，且本 change 不解决"为什么 PG 没起"的核心问题。

### D6. 测试策略：mock DataSource，不引入 Testcontainers
**选择**：`HealthServiceTest` 用 Mockito mock `DataSource.getConnection()` 抛 `SQLException`（含 "Connection refused" 子串），断言 `message` 字段内容。
**理由**：测试是 unit 级别的，连真实 PG 反而绕弯。
**替代方案考虑**：
- 真实 PG 跑测试：要本地 PG 已起，且 CI 也要起——本 change 正是要降低"必须起 docker PG"的耦合。

### D7. m1-project-management 文档同步策略
**选择**：`design.md` Decision 8 整段改写；`tasks.md` 任务 2.1 改写、第 17 节备注简化；`proposal.md` 测试影响改写。
**理由**：m1 已 archive-ready 但未 archive，未来 archive 时这些文本会成为 spec delta 的对照——必须与新策略一致。
**替代方案考虑**：
- 整 m1 archive 掉：超出 scope（m1 与 PG 启动是正交 concern）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|---|---|---|
| **R1. 文档遗漏**：docs/ 6 个文件、openspec/ 8 个文件、根目录 2 个文件都可能漏改 | 新人按 docker compose 启动会失败 | tasks.md 用文件级 checklist 强制逐一勾选；review 时用 `grep -ri "docker" docs/ openspec/ backend/ frontend/ README.md AGENTS.md` 验证 |
| **R2. SQLException 消息字符串匹配脆弱** | 不同 PG JDBC 驱动版本/POSIX 错误文案差异，导致 `contains("Connection refused")` 不命中 | 兜底分支返通用提示 "数据库连接失败，请检查 docs/runbook.md"；后续可引入 `PSQLException` 状态码（SQLState `08006`/`57P01`）做更稳的识别，但本 change 不做 |
| **R3. HealthResponse 字段加 `message` 破坏现有 API 消费者** | 前端 WelcomePage.vue 解析响应若用 strict schema 会失败 | 字段是 nullable，前端 `data.message` 访问仍是 undefined-safe；JSON 反序列化向后兼容 |
| **R4. 删除 docker-compose.yml 后用户机器上残留的 `visual_spider4_postgres` 容器** | 不影响功能，但 `docker ps` 仍能看到孤儿容器 | 文档（runbook.md 排错段）加一句"如需清理：`docker rm -f visual_spider4_postgres`"；不强制要求用户清理 |
| **R5. mvn test 启动慢或 37 测试中有依赖 docker 的** | 测试套件失败 | tasks.md 实施时先跑一次 baseline `mvn test`；如确实有，再回头评估（搜索结果显示无） |
| **R6. 用户机器没装 PG / 装的是 MySQL** | `pg_isready` / `psql` 命令不存在 | runbook.md 排错章节给出"安装指引"分支（Windows 用 EDB installer / macOS 用 Postgres.app / Linux 用 apt/yum） |
| **R7. agent 误判** | agent 把非 PG 错误当成 PG 未启动提示用户 | HealthService 的 `message` 字段加稳定前缀（如 "PG_NOT_READY: "），agent 严格匹配前缀 |

## Migration Plan

### 部署步骤（人工执行）

1. **拉取最新代码** → `git pull`
2. **停掉旧容器**（如有）：`docker stop visual_spider4_postgres && docker rm visual_spider4_postgres`
3. **本机安装并启动 PG**（一次性，OS 不同命令不同，详见 runbook.md）：
   - Windows: 下载 EDB PostgreSQL 16 installer，安装时设密码 `123456`，用 Services 启动
   - macOS: `brew install postgresql@16 && brew services start postgresql@16`
   - Linux: `sudo apt install postgresql-16 && sudo systemctl start postgresql`
4. **建库**：`psql -U postgres -c "CREATE DATABASE visual_spider4;"`（若已存在会报错，可忽略）
5. **建 test 库**：`psql -U postgres -c "CREATE DATABASE visual_spider4_test;"`
6. **跑测试**：`cd backend && mvn test`（应 37 通过）
7. **启动后端**：`mvn spring-boot:run`，看到 `Started Application in X seconds` 与 `database: UP` 即可

### 回滚策略
- 本 change 主要是**文档 + 配置 + 测试代码**调整，不改业务逻辑，回滚成本低。
- 真要回滚：`git revert` 本次提交，重新 `docker compose up -d` 即可（前提是 docker-compose.yml 还在版本库里——本 change 会删除它，回滚时需从 git 历史恢复）。
- 数据无影响（库 schema 没变，库内容无破坏性操作）。

### 验证
- `mvn test` — 37 项全过
- `mvn spring-boot:run`（PG 停掉） — 启动日志含 banner；`curl localhost:8080/api/v1/health` 返 `database: DOWN` + `message: "PostgreSQL 未启动..."`
- `mvn spring-boot:run`（PG 起来后） — 启动成功；`curl localhost:8080/api/v1/health` 返 `database: UP` + `message: null`
- `openspec validate dev-environment --strict` 通过

## Open Questions

> 用户已决：
> - Q1 `docker-compose.yml` 去留 → **A 删**
> - Q2 Testcontainers 依赖 + `IntegrationTestBase.java` → **清理**
> - Q3 归档目录 4 文件 → **保持原样**
> - Q4 "主动提示"含义 → **agent 在会话中直接告知，不做程序侧 hook**

> 实施时再决（不阻塞 plan，可边做边调）：
> - SQLException 消息字符串匹配 vs SQLState 码识别：先做字符串匹配 + 通用兜底，后续如需更精准再加 SQLState。
> - 启动 banner 的 ASCII 字符（`===` 还是 `###` 还是 `┌─┐` 框）：实施时由开发者本人审美定，本设计不定死。
> - IntegrationTestBase.java 真的删还是留作未来 CI 选项：建议删（决策 2 已确定"清理"），但若 review 阶段有人反对，5 分钟内可恢复。
