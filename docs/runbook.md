# 运维手册

## 启动顺序

```
1. PostgreSQL（端口 5432）
       ↓
2. Backend Spring Boot（端口 8080）
       ↓
3. Frontend Vite（端口 5173，代理 /api → 8080）
```

## PostgreSQL

### 启动

```bash
# 方式 A：docker compose（推荐）
docker compose up -d
# 容器名：visual_spider4_postgres，用户 postgres，密码 123456，库 visual_spider4

# 方式 B：复用本机已有容器
# 项目约定：直接用名为 postgresql 的容器
```

### 验证

```bash
docker ps | grep postgres
# 看到 Up 状态即健康
```

### 表结构

dev 模式 `application.yml` 设 `ddl-auto: update`，启动时自动建表/更新表。
test 模式 `application-test.yml` 设 `ddl-auto: create-drop`，测试前后建/删表。

> ⚠️ **生产环境不要用 ddl-auto**。M2+ 应引入 Flyway / Liquibase 管理 migration。

### 创建 test 库

如果 `application-test.yml` 指向的 `visual_spider4_test` 不存在：

```bash
docker exec postgresql psql -U postgres -c "CREATE DATABASE visual_spider4_test;"
```

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

### 跑测试

```bash
cd backend
mvn test                                   # 跑所有 37 项测试
mvn test -Dtest=CrawlConfigServiceTest     # 跑单个类
mvn test -Dtest='*ServiceTest'             # 按通配符
```

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

> **注意**：M1 前端组件未写 vitest 测试，仅配置了测试框架。M2+ 应补齐 store / page 测试。

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
# {"code":200,"data":{"status":"UP","database":"UP","timestamp":"..."},"message":"success"}

# 2. 前端起来后，浏览器打开
open http://localhost:5173

# 3. 创建配置
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"name":"测试","pageType":"LIST_DETAIL","selectorType":"CSS"}'

# 4. 列出
curl http://localhost:8080/api/v1/configs
```

## Known Issues

### 1. Testcontainers 1.20.x 与 Docker Desktop 29.x 不兼容

**症状**：
```
Caused by: java.lang.IllegalStateException: Could not find a valid Docker environment
NpipeSocketClientProviderStrategy: failed with BadRequestException (Status 400)
```

**根因**：docker-java 客户端通过 npipe 调用 Docker Desktop 时，Docker Desktop 29.x 返回的 `/info` 响应格式有变更，导致 Testcontainers 拿不到正常 docker info。

**当前方案**（已实施）：
- 集成测试改用本机 PostgreSQL 容器
- `application-test.yml` 通过 `@ActiveProfiles("test")` 激活
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` 连接到 `visual_spider4_test` 库
- Testcontainers 依赖保留在 `pom.xml` 供 CI/未来使用

**切换回 Testcontainers**（CI 环境）：
- 删除 `@ActiveProfiles("test")` 或改 profile 名
- 在测试基类加 `@Testcontainers` + `@Container PostgreSQLContainer` + `@DynamicPropertySource`

### 2. 中文 Windows 终端的编码问题

**症状**：`curl` 返回的 JSON 中中文字段显示为 `?????`。

**原因**：Windows PowerShell 默认 GBK 编码，JSON 里的 UTF-8 中文无法正确显示。

**解决**：用浏览器（前端 Vue 页面）查看数据；或 PowerShell 7+；或 `cmd /c chcp 65001`。

### 3. Maven 测试首次运行慢

**症状**：第一次跑 `mvn test` 等待 5-10 分钟。

**原因**：下载 Spring Boot / JPA / Mockito / Testcontainers 等依赖。

**解决**：一次性下载；后续增量构建快。

## 故障排查

### 后端启动失败：`Communications link failure`

数据库没起来或连接参数错。
1. `docker ps | grep postgres` 看 PG 是否运行
2. 检查 `DB_HOST` / `DB_PORT` 环境变量
3. 测连通性：`psql -h localhost -U postgres -d visual_spider4`

### 前端启动失败：`Cannot find module 'vue-router'`

依赖没装：`cd frontend && npm install`

### 跑测试卡在 `Creating PostgreSQL container`

Testcontainers 拉镜像/启动慢，等几分钟。如果超过 5 分钟仍无响应，看 Known Issues #1。

### 前端代理不生效：前端 404

后端没起。`curl http://localhost:8080/api/v1/health` 验证。

## 端口冲突

| 端口 | 占用 | 处理 |
|------|------|------|
| 8080 | 其他 Java 应用 | `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081"` |
| 5173 | 其他 Vite | `npm run dev -- --port 5174` |
| 5432 | 其他 PG | 修改 `docker-compose.yml` 端口映射或 `DB_PORT` 环境变量 |
