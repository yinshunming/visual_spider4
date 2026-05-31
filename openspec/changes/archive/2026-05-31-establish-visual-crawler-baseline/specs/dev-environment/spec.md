# dev-environment（开发环境基线）

## Purpose

建立可视化爬虫项目的最小可运行开发环境，包括后端工程骨架、前端工程骨架、数据库容器配置和健康检查能力。本 capability 不实现任何爬虫业务逻辑，仅确保开发者可以在本地启动完整的最小运行环境。

## ADDED Requirements

### Requirement: 后端健康检查接口

后端服务 SHALL 提供 `/api/v1/health` HTTP GET 接口，返回 JSON 格式的健康状态，包含服务状态、数据库连接状态和时间戳。

#### Scenario: 健康检查成功
- **WHEN** 客户端发送 GET 请求到 `/api/v1/health`
- **THEN** 系统返回 HTTP 200，响应体为 `{"code":200,"data":{"status":"UP","database":"UP","timestamp":"..."},"message":"success"}`

#### Scenario: 数据库连接失败时
- **WHEN** 数据库连接不可用时客户端发送 GET 请求到 `/api/v1/health`
- **THEN** 系统返回 HTTP 200，响应体中 `database` 字段为 `"DOWN"`，`message` 为 `"database connection failed"`

### Requirement: 前端欢迎页面

前端应用 SHALL 在根路径 `/` 提供欢迎页面，返回 HTML 内容而非 404。

#### Scenario: 访问根路径
- **WHEN** 用户在浏览器访问 `http://localhost:5173/`
- **THEN** 页面显示欢迎内容，非 HTTP 404

#### Scenario: 前端开发服务器运行中
- **WHEN** 前端 `pnpm dev` 成功启动
- **THEN** Vite 开发服务器在端口 5173 监听请求

### Requirement: PostgreSQL 数据库容器

系统 SHALL 提供 Docker Compose 配置，启动 PostgreSQL 容器并创建 `visual_spider4` 数据库。

#### Scenario: 启动数据库容器
- **WHEN** 开发者执行 `docker compose up -d`
- **THEN** PostgreSQL 容器启动，端口 5432 可访问，数据库 `visual_spider4` 已创建

#### Scenario: 数据库连接参数
- **WHEN** 后端服务启动并配置数据源
- **THEN** 系统使用以下连接参数连接到 PostgreSQL：
  - Host: localhost
  - Port: 5432
  - Database: visual_spider4
  - Username: postgres
  - Password: 123456

### Requirement: 后端工程结构

后端项目 SHALL 遵循以下包结构：
```
com.visualspider
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── exception/
```

#### Scenario: 后端编译通过
- **WHEN** 开发者执行 `mvn clean compile`
- **THEN** 编译成功，无错误

#### Scenario: 后端启动成功
- **WHEN** 开发者执行 `mvn spring-boot:run`
- **THEN** Spring Boot 应用启动，日志显示 `Started Application in X seconds`

### Requirement: 前端工程结构

前端项目 SHALL 遵循以下结构：
```
frontend/
├── src/
│   ├── views/
│   ├── stores/
│   ├── api/
│   └── main.js
├── index.html
└── vite.config.js
```

#### Scenario: 前端构建通过
- **WHEN** 开发者执行 `pnpm build`
- **THEN** 构建成功，生成 dist/ 目录

### Requirement: 统一响应格式

后端 API SHALL 使用统一响应格式：

#### Scenario: 成功响应格式
- **WHEN** 任何 API 成功返回数据
- **THEN** 响应体为 `{"code":200,"data":<actual_data>,"message":"success"}`

#### Scenario: 错误响应格式
- **WHEN** API 返回错误
- **THEN** 响应体为 `{"code":<error_code>,"data":null,"message":"<error_description>"}`

### Requirement: 环境变量配置

后端 SHALL 支持通过环境变量或 .env 文件配置数据库连接。

#### Scenario: 使用默认环境变量
- **WHEN** 后端启动时未设置任何数据库环境变量
- **THEN** 系统使用默认值：localhost:5432/visual_spider4, postgres/123456

#### Scenario: 使用自定义环境变量
- **WHEN** 环境变量 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 设置
- **THEN** 系统使用这些自定义值覆盖默认值
