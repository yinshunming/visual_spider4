## MODIFIED Requirements

### Requirement: 本机 PostgreSQL 服务

系统 SHALL 假设开发者在本机手工运行 PostgreSQL 16+ 服务（默认 localhost:5432），库 `visual_spider4` 已存在。后端 SHALL 在启动时检测数据库连通性；连接失败时，HealthService SHALL 返回 HTTP 200 响应体（`{"code":200,"data":{"status":"UP","database":"DOWN","message":"<可读中文提示>","timestamp":"..."},"message":"success"}`），且后端启动日志 SHALL 打印醒目的多行 ASCII banner 说明"PostgreSQL 未启动"并指引用户手工启动。

#### Scenario: 启动后端时本机 PG 可用
- **WHEN** 开发者在本机手工启动 PostgreSQL 服务后执行 `mvn spring-boot:run`
- **THEN** Spring Boot 应用启动成功；`GET /api/v1/health` 返回 `{"code":200,"data":{"status":"UP","database":"UP","message":null,"timestamp":"..."},"message":"success"}`

#### Scenario: 启动后端时本机 PG 未启动
- **WHEN** 开发者执行 `mvn spring-boot:run` 且本机 PostgreSQL 服务未运行
- **THEN** Spring Boot 应用仍能完成 Spring 上下文初始化；启动日志含多行 ASCII banner（关键字"PostgreSQL 未启动"）；`GET /api/v1/health` 返回 `{"code":200,"data":{"status":"UP","database":"DOWN","message":"PostgreSQL 未启动，请手工启动本机服务（默认端口 5432），启动后告诉我继续。详见 docs/runbook.md。","timestamp":"..."},"message":"success"}`

#### Scenario: 数据库不存在
- **WHEN** 开发者启动本机 PG 服务但未创建 `visual_spider4` 库
- **THEN** `GET /api/v1/health` 返回 `database: "DOWN"` 且 `message` 含"FATAL: database "visual_spider4" does not exist"的可读中文提示（如"数据库 visual_spider4 不存在，请先 `psql -U postgres -c 'CREATE DATABASE visual_spider4;'`"）

#### Scenario: 数据库连接参数
- **WHEN** 后端服务启动并配置数据源
- **THEN** 系统使用以下连接参数连接到 PostgreSQL：
  - Host: `${DB_HOST:localhost}`
  - Port: `${DB_PORT:5432}`
  - Database: `${DB_NAME:visual_spider4}`
  - Username: `${DB_USERNAME:postgres}`
  - Password: `${DB_PASSWORD:123456}`

## REMOVED Requirements

### Requirement: PostgreSQL 数据库容器
**Reason**: 开发数据库不再由 docker 启动；该 requirement 描述的"Docker Compose 配置 + `docker compose up -d` 启动容器"行为与新策略冲突。`docker-compose.yml` 已删除，docker 启动流程已废弃。
**Migration**: 开发者 SHALL 在本机手工启动 PostgreSQL 服务（Windows / macOS / Linux 启动命令详见 `docs/runbook.md`）。后端通过 `application.yml` 的 `${DB_HOST:localhost}:${DB_PORT:5432}` 默认值直接连接。
