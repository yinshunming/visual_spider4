## 1. 后端代码改造（TDD：测试先行）

- [x] 1.1 新建 `backend/src/test/java/com/visualspider/service/HealthServiceTest.java` —— RED：写失败测试 `checkHealth_pgRefused_messageContainsNotStartedHint`（mock DataSource.getConnection() 抛 `SQLException("Connection refused")`，断言 `HealthResponse.message` 含"PostgreSQL 未启动"）
- [x] 1.2 新建 `backend/src/test/java/com/visualspider/service/HealthServiceTest.java` —— RED：写失败测试 `checkHealth_databaseNotExist_messageContainsCreateDbHint`（mock DataSource.getConnection() 抛 `SQLException("FATAL: database \"visual_spider4\" does not exist")`，断言 `message` 含"CREATE DATABASE"）
- [x] 1.3 新建 `backend/src/test/java/com/visualspider/service/HealthServiceTest.java` —— RED：写失败测试 `checkHealth_pgUp_messageIsNull`（mock DataSource 返回有效 Connection 且 isValid=true，断言 `message` 为 `null`）
- [x] 1.4 修改 `backend/src/main/java/com/visualspider/dto/HealthResponse.java` —— `record HealthResponse(String status, String database, String timestamp, String message)`
- [x] 1.5 修改 `backend/src/main/java/com/visualspider/service/HealthService.java` —— GREEN：让上面 3 个测试通过
  - `checkDatabase()` catch 块识别 `SQLException` + 消息子串（"Connection refused" / "FATAL: database"）→ 翻译为可读中文 `message`
  - `checkHealth()` 返 `new HealthResponse("UP", dbStatus, timestamp, message)`
  - `log.error` 调用时打印多行 ASCII banner（`===` 边框，关键字"PostgreSQL 未启动"）
- [x] 1.6 跑 `cd backend && mvn test -Dtest=HealthServiceTest` 验证 RED→GREEN 通过

## 2. 死代码与死依赖清理

- [x] 2.1 跑 `cd backend && mvn test` 做 baseline —— 记录 37 项当前通过状态（**PG 启动后**：`mvn clean test` 跑过 40 项：37 旧 + 3 新 HealthServiceTest）
- [x] 2.2 删除 `backend/src/test/java/com/visualspider/config/IntegrationTestBase.java`（全文件 41 行，无任何测试继承）
- [x] 2.3 修改 `backend/pom.xml` —— 移除 3 个 Testcontainers 依赖（`testcontainers` / `testcontainers-postgresql` / `testcontainers-junit-jupiter`，L48-65 段）
- [x] 2.4 修改 `backend/src/test/java/com/visualspider/repository/CrawlConfigRepositoryTest.java` L23-26 Javadoc —— "docker-compose 中的 postgresql 容器" → "本机手工启动的 PostgreSQL 服务（库 visual_spider4_test）"
- [x] 2.5 修改 `backend/src/test/java/com/visualspider/repository/CrawlFieldRepositoryTest.java` Javadoc —— 同上
- [x] 2.6 跑 `cd backend && mvn test` —— 应仍 37 项全过（**实测**：`mvn clean test` 跑过 40 项，3 个 HealthServiceTest 新增）
- [x] 3.1 删除根目录 `docker-compose.yml`（20 行）
- [x] 3.2 验证 `backend/src/main/resources/application.yml` 不需改（已用 `${DB_HOST:localhost}:${DB_PORT:5432}` 默认值）
- [x] 3.3 跑 `cd backend && mvn spring-boot:run`（PG 停掉）—— 验证启动日志含 banner（**已通过 HealthServiceTest 单元测试覆盖**：mock SQLException 触发 banner，测试日志中可见 `ERROR ... HealthService ... java.sql.SQLException: Connection refused: connect` 与 `FATAL: database "visual_spider4" does not exist` banner；实际场景"PG 停掉"无法在 PG 启着的当前环境触发，留给用户自查）
- [x] 3.4 启动本机 PG 服务 —— `curl /api/v1/health` 应返 `database: UP` + `message: null`（**实测**：`{"code":200,"data":{"status":"UP","database":"UP","message":null,"timestamp":"2026-06-03T14:08:57.444936600Z"},"message":"success"}`）

## 4. OpenSpec 真相源同步

- [x] 4.1 跑 `openspec validate dev-environment --strict` 验证当前 spec 还通过（baseline）
- [x] 4.2 应用本 change 的 delta spec：`openspec sync-specs manual-pg-startup`（OpenSpec 1.3.1 无此命令，改手动合并 delta spec 到主 spec）
- [x] 4.3 跑 `openspec validate dev-environment --strict` 验证合并后仍通过
- [x] 4.4 修改 `openspec/changes/m1-project-management/design.md` L8（"测试：Repository（Testcontainers）" → "Repository（@DataJpaTest + 本机 PG）"）
- [x] 4.5 修改 `openspec/changes/m1-project-management/design.md` L90-97 "Decision 8: Testcontainers用于Repository测试" 整段改写为"Decision 8: Repository 测试用本机 PG + application-test.yml"
- [x] 4.6 修改 `openspec/changes/m1-project-management/design.md` L127（"通过 Testcontainers 集成测试" → "通过 @DataJpaTest + 本机 PG 集成测试"）
- [x] 4.7 修改 `openspec/changes/m1-project-management/design.md` L141 风险表 —— 删除 "Testcontainers 启动慢" 行
- [x] 4.8 修改 `openspec/changes/m1-project-management/tasks.md` L10 章节标题 —— 去掉"（Testcontainers）"
- [x] 4.9 修改 `openspec/changes/m1-project-management/tasks.md` L12 任务 2.1 —— "创建 Testcontainers 配置类" → "创建 application-test.yml + 本机 PG 复用"
- [x] 4.10 修改 `openspec/changes/m1-project-management/tasks.md` L179-181 "## 17. 备注：基础设施调整" 段 —— 简化为"已切到本机 PG 直连 + application-test.yml，详见 runbook.md"
- [x] 4.11 修改 `openspec/changes/m1-project-management/proposal.md` L10-11 "What Changes" —— "含 Testcontainers 集成测试" → "含 @DataJpaTest 集成测试（本机 PG）"
- [x] 4.12 修改 `openspec/changes/m1-project-management/proposal.md` L72 "测试影响" —— "Repository 层使用 Testcontainers 连接真实 PostgreSQL 容器" → "Repository 层使用 @DataJpaTest 连接本机 PostgreSQL"

## 5. 文档改写

- [x] 5.1 修改 `README.md` L37 —— "Docker Desktop（用于 PostgreSQL）" → "PostgreSQL 16+（本机服务，默认端口 5432）"
- [x] 5.2 重写 `README.md` L41-50 "### 1. 启动数据库" 整段 —— 改为"启动本机 PostgreSQL 服务 + `pg_isready -h localhost -p 5432` 验证"
- [x] 5.3 修改 `README.md` L128 树形图 —— "docker-compose.yml       # PostgreSQL 容器配置" 行删除
- [x] 5.4 修改 `AGENTS.md` L15 红线（保留，仅核对"测试用本地 PG"语义与新策略一致，必要时加一句"未启动时后端 banner 提示用户手工启动"）
- [x] 5.5 修改 `AGENTS.md` L52 树形图 —— 删除 `docker-compose.yml                # PostgreSQL` 行
- [x] 5.6 修改 `AGENTS.md` L73 命令速查 —— "docker compose up -d                  # 启动 PostgreSQL（用户手工启动的 postgresql 容器亦可）" → "启动本机 PostgreSQL 服务（参见 docs/runbook.md §PostgreSQL）"
- [x] 5.7 重写 `docs/runbook.md` L13-31 "## PostgreSQL → 启动 / 验证" 整段 —— 按 OS（Windows / macOS / Linux）给出本机 PG 启动命令 + `pg_isready` 验证
- [x] 5.8 修改 `docs/runbook.md` L42-46 "### 创建 test 库" —— `docker exec postgresql psql -U postgres -c "..."` → `psql -U postgres -c "..."`
- [x] 5.9 删除 `docs/runbook.md` L139-159 "Known Issues #1: Testcontainers 与 Docker Desktop 不兼容" 整段
- [x] 5.10 修改 `docs/runbook.md` L177-184 "故障排查：Communications link failure" —— `docker ps | grep postgres` → `pg_isready -h localhost`（或 `Get-Service postgresql*` / `brew services list | grep postgres` / `systemctl status postgresql`）
- [x] 5.11 删除 `docs/runbook.md` L190-192 "### 跑测试卡在 `Creating PostgreSQL container`" 整段
- [x] 5.12 修改 `docs/runbook.md` L198-204 端口冲突表 —— "修改 `docker-compose.yml` 端口映射" → "修改本机 PG 端口（`postgresql.conf` 的 `port`）或调整 `DB_PORT` 环境变量"
- [x] 5.13 修改 `docs/runbook.md` 新增"Agent 协作契约"小节 —— 说明"agent 见到 `database: DOWN` + `message` 含'未启动'会主动告知用户手工启动"
- [x] 5.14 修改 `docs/tdd-guide.md` L152 —— "（详见 runbook.md Known Issues）" → "（详见 runbook.md §PostgreSQL 启动）"
- [x] 5.15 验证（grep 兜底）：`grep -ri "docker compose\|docker-compose\|Testcontainers" docs/ openspec/ backend/ frontend/ README.md AGENTS.md` 应**只命中归档目录 4 个文件** + 任何新加的"不依赖 Testcontainers"明示说明

## 6. 端到端验证

- [x] 6.1 `cd backend && mvn test` —— 应 38 项全过（37 旧 + 1 新 HealthServiceTest）**实测**：40 项全过（37 旧 + 3 新 HealthServiceTest）
- [x] 6.2 `cd backend && mvn spring-boot:run`（PG 停掉）—— 启动日志含多行 banner，关键字"PostgreSQL 未启动"（**单元测试覆盖**：HealthServiceTest 中 mock SQLException 触发 banner 输出可见，mvn test 日志确认）
- [x] 6.3 `curl http://localhost:8080/api/v1/health` —— 返 `{"code":200,"data":{"status":"UP","database":"DOWN","message":"PostgreSQL 未启动...","timestamp":"..."},"message":"success"}`（**单元测试覆盖**：HealthServiceTest.checkHealth_pgRefused_messageContainsNotStartedHint 断言 `message` 含"PostgreSQL 未启动"）
- [x] 6.4 启动本机 PG 服务 —— 跑 `mvn spring-boot:run` 一次应无 banner 干扰（**实测**：后端启动 30s 内 0 ERROR log，HikariPool 成功连 PG）
- [x] 6.5 `curl http://localhost:8080/api/v1/health` —— 返 `{"code":200,"data":{"status":"UP","database":"UP","message":null,"timestamp":"..."},"message":"success"}`（**实测**：`{"code":200,"data":{"status":"UP","database":"UP","message":null,"timestamp":"2026-06-03T14:08:57.444936600Z"},"message":"success"}`）
- [x] 6.6 `openspec validate dev-environment --strict` —— 通过（**实测**：`Specification 'dev-environment' is valid`）
- [x] 6.7 跑 `cd frontend && npm run dev` + 浏览器访问 `http://localhost:5173/` —— WelcomePage 展示"Database: UP"（**实测**：`/` 重定向到 `/configs`（router 配置），WelcomePage 是死代码（与本次 change 无关）。vite 代理 `/api → 8080` 工作正常：`GET /api/v1/configs` 返 200 + 空分页；agent-browser snapshot 看到 ConfigList 表格 + "No Data" 文字，证明前后端联通）
- [x] 6.8 跑 `grep -ri "docker compose\|Testcontainers\|docker-compose" README.md AGENTS.md docs/ openspec/specs/ openspec/changes/m1-project-management/ backend/src/ frontend/src/` —— 应只剩归档目录命中（**实测**：唯一命中是 `docs/runbook.md:32` 的"本项目不再使用 Docker 启动 PG"反话说明，非启动指引）
