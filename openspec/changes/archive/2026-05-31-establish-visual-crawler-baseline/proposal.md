## Why

当前项目仅有 OpenSpec specs 定义，但缺乏可运行的基础工程。开发者无法在本地启动后端、前端和数据库来验证系统是否正常工作。建立开发基线后，开发者可以在本地启动完整的最小运行环境，访问健康检查接口，确认系统具备后续业务开发的最小前提条件。

## What Changes

1. **后端工程骨架**
   - Spring Boot 3.x 基础项目结构（Controller/Service/Repository/Entity 分层）
   - `/api/v1/health` 健康检查接口（验证服务可用性）
   - PostgreSQL 数据源配置
   - 统一响应格式（code/data/message）

2. **前端工程骨架**
   - Vue 3 + Vite 基础项目
   - Element Plus 组件库集成
   - 基础页面入口（/ 根路径返回简单欢迎页）
   - Axios 封装与 API 基础路径配置

3. **数据库环境**
   - Docker Compose PostgreSQL 配置
   - 数据库 `visual_spider4` 自动创建
   - 连接凭证：postgres/123456

4. **本地开发文档**
   - 后端启动方式（mvn spring-boot:run）
   - 前端启动方式（pnpm dev）
   - 环境变量配置说明
   - 端口映射（后端:8080，前端:5173，数据库:5432）

## Capabilities

### New Capabilities
- `dev-environment`: 本地开发环境基线 — 包含后端工程骨架、前端工程骨架、数据库容器配置和健康检查能力。此 capability 不实现任何爬虫业务逻辑，仅建立可运行的最小开发环境。

### Modified Capabilities
<!-- 项目基础设施不涉及现有 spec 的需求变更 -->
- 无

## Impact

- **新增目录**: `backend/`（Spring Boot 项目）、`frontend/`（Vue 项目）
- **新增文件**: Docker Compose 配置、健康检查 Controller、基础前端页面
- **端口占用**: 8080（后端）、5173（前端 Vite）、5432（PostgreSQL）
- **依赖**: Spring Boot 3.x、JDK 21+、Node.js 18+、Docker Desktop
