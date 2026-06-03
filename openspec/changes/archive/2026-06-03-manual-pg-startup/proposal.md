## Why

当前项目通过 `docker-compose.yml` 启动 PostgreSQL 容器作为开发数据库。M1 实施过程中已经切到本机 PG 直连（`runbook.md` Known Issues #1），但仓库里仍残留 docker 启动文档、`docker-compose.yml`、Testcontainers 死代码与死依赖、错误的"健康检查 DOWN"语义——一旦 PG 未启动，用户/agent 拿不到明确指引。本次 change 把开发数据库启动方式从"docker compose up"切换为"开发者本机手工启动"，并把后端错误信号打磨到 agent 在会话中能直接识别并主动提示用户。

## What Changes

### 行为变更
- **BREAKING** 删除 `docker-compose.yml`（开发数据库不再由 docker 启动）。
- 开发者 SHALL 在本机手工启动 PostgreSQL 16+ 服务（端口 5432，库 `visual_spider4`，用户 `postgres`，密码 `123456`），后端程序直接连接。
- 后端健康检查在数据库不可用时 SHALL 返回结构化的"未启动"信号（`database: DOWN` + `message` 字段含"PostgreSQL 未启动"等可读文本），便于 agent 在会话中直接识别并提示用户手工启动。

### 文档变更
- `README.md`、`AGENTS.md` 中所有"docker compose up -d" / "Docker Desktop 用于 PostgreSQL" / `docker-compose.yml` 容器配置注释 → 改为"启动本机 PostgreSQL 服务（参见 docs/runbook.md）"。
- `docs/runbook.md` 整段 PostgreSQL 启动/验证/排错章节重写；删除 `Known Issues #1`（Testcontainers 与 Docker Desktop 不兼容）整段；删除"测试卡在 Creating PostgreSQL container"排错段；端口冲突表改写。
- `docs/tdd-guide.md` 第 152 行"详见 runbook.md Known Issues"链接对齐全新口径。

### 依赖与死代码清理
- **BREAKING** 移除 `backend/pom.xml` 的 3 个 Testcontainers 依赖（`testcontainers` / `testcontainers-postgresql` / `testcontainers-junit-jupiter`）。
- **BREAKING** 删除 `backend/src/test/java/com/visualspider/config/IntegrationTestBase.java`（全仓无任何测试继承，是死代码）。
- `CrawlConfigRepositoryTest.java` / `CrawlFieldRepositoryTest.java` Javadoc 中"docker-compose 中的 postgresql 容器" → "本机手工启动的 PostgreSQL 服务"。

### 同步
- `openspec/changes/m1-project-management/{design,tasks,proposal}.md` 中所有 Testcontainers / Docker Desktop 引用与新策略对齐。

## Capabilities

### New Capabilities
无。

### Modified Capabilities
- `dev-environment`: 把 `Requirement: PostgreSQL 数据库容器` 改为"本机 PostgreSQL 服务"——`SHALL` 改为"开发者本机手工运行 PostgreSQL"，Scenario 从"WHEN 开发者执行 docker compose up -d"改为"WHEN 后端启动且 PG 未运行 → THEN 健康检查返回 503 + 明确启动指引"。同步把 `Purpose` 段"数据库容器配置"改为"本机数据库要求"。

## Impact

### 后端影响
- 修改 `backend/src/main/java/com/visualspider/dto/HealthResponse.java` — 加 `message` 字段（可空，UP 时为 `null`/省略，DOWN 时含可读提示文本）。
- 修改 `backend/src/main/java/com/visualspider/service/HealthService.java` — `checkDatabase()` catch 块把 `SQLException`/`PSQLException` 的连接失败（`Connection refused` / `FATAL: database "..." does not exist`）翻译成结构化 message；启动日志加醒目 banner。
- 删除 `backend/src/test/java/com/visualspider/config/IntegrationTestBase.java`（41 行，无引用）。
- 修改 `backend/src/test/java/com/visualspider/repository/CrawlConfigRepositoryTest.java` Javadoc L23-26。
- 修改 `backend/src/test/java/com/visualspider/repository/CrawlFieldRepositoryTest.java` Javadoc。
- 修改 `backend/pom.xml` L48-65 — 移除 3 个 Testcontainers 依赖。

### 配置影响
- 删除 `docker-compose.yml`（20 行）。

### 文档影响
- `README.md`（启动数据库章节 + 项目结构图）
- `AGENTS.md`（开发命令速查 + 仓库结构图）
- `docs/runbook.md`（PostgreSQL 启动/验证/排错整段 + Known Issues #1 整段 + 端口冲突表）
- `docs/tdd-guide.md`（L152 交叉引用）

### OpenSpec 影响
- `openspec/specs/dev-environment/spec.md`（Requirement + Scenario 改写）
- `openspec/changes/m1-project-management/design.md`（Decision 8 改写）
- `openspec/changes/m1-project-management/tasks.md`（章节标题 + 任务 2.1 + 备注 17 改写）
- `openspec/changes/m1-project-management/proposal.md`（What Changes + 测试影响改写）
- 归档目录 `openspec/changes/archive/2026-05-31-establish-visual-crawler-baseline/*` 保持原样（历史快照，不修改）。

### 行为约定（agent 协作面）
本项目的 AI agent（开发期在 IDE 会话中）应**在执行后端启动 / 测试 / 任何需要 PG 的命令前后，主动检测 PG 状态**。当命令因 PG 不可用失败时，agent SHALL **直接在当前会话中**告知用户："PostgreSQL 未启动，请手工启动本机 PostgreSQL 服务（端口 5432，库 `visual_spider4`），启动后告诉我继续"，而不是返回 stack trace 等待用户自己看。HealthService 返回的 `message` 字段是 agent 识别的契约信号。

### 验证
- `mvn test` — 37 测试仍通过（已不依赖 Testcontainers）。
- `mvn spring-boot:run`（PG 未启动）— 启动日志含醒目 banner；`GET /api/v1/health` 返回 `database: DOWN` + `message` 含"未启动"。
- `mvn spring-boot:run`（PG 启动后）— 启动成功；`GET /api/v1/health` 返回 `database: UP`。
- `openspec validate dev-environment --strict` 通过。
