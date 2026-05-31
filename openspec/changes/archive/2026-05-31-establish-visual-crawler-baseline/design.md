## Context

当前项目状态：仅有 OpenSpec specs 定义（8 个 capability specs），缺乏实际可运行的代码。开发者无法在本地验证系统是否正常。

**技术栈选择**（来自 AGENTS.md）:
- 后端：Spring Boot 3.x + JPA + PostgreSQL + WebSocket
- 前端：Vue 3 + Vite + Element Plus + Pinia + Axios
- 数据库：PostgreSQL（Docker 启动）
- 动态页面引擎：Playwright（后续迭代引入）

**约束**：
- MVP 为单用户单会话，无认证
- 系统边界遵循 system-boundaries spec 定义
- 项目管理遵循 project-management spec 定义的配置生命周期

## Goals / Non-Goals

**Goals:**
- 后端 Spring Boot 项目可独立启动，端口 8080
- 前端 Vue 项目可独立启动，端口 5173
- PostgreSQL 容器可启动，数据库 visual_spider4 可连接
- `/api/v1/health` 返回 200 OK
- 前端根路径 `/` 返回欢迎页面（非 404）
- 明确本地开发环境启动步骤

**Non-Goals:**
- 不实现任何爬虫业务逻辑（无 CRUD、无选择器、无爬取执行）
- 不实现登录、权限、多租户
- 不实现 WebSocket 实时通道
- 不实现 Playwright 浏览器控制
- 不实现数据持久化表结构（JPA Entity 仅为健康检查验证连接）

## Decisions

### Decision 1: 项目结构采用前后端分离架构

**选择**: `backend/` 和 `frontend/` 作为独立项目目录

**理由**:
- 前端 Vue 3 + Vite 技术栈与后端 Spring Boot 完全独立
- 前后端可独立开发、测试、部署
- 避免单体架构的复杂性

**替代方案考虑**:
- Monorepo 在 MVP 阶段不必要的复杂度
- 微服务架构过于超前

### Decision 2: 数据库使用 Docker Compose 管理

**选择**: docker-compose.yml 包含 PostgreSQL 服务

**理由**:
- 一键启动完整开发环境
- 与生产环境（独立 PostgreSQL）配置一致
- 开发者无需手动安装 PostgreSQL

**替代方案考虑**:
- 本地安装 PostgreSQL：环境差异大，难以标准化
- 云数据库：无法离线开发，增加网络依赖

### Decision 3: 健康检查验证数据库连接

**选择**: `/api/v1/health` 端点检查数据库连接并返回状态

**理由**:
- 不仅验证 HTTP 服务存活，还验证数据源配置正确
- 早期发现数据库连接问题
- 为后续 actuator 端点扩展奠定基础

**替代方案考虑**:
- 仅返回固定字符串：无法验证数据库连接
- Spring Boot Actuator：MVP 阶段过于重型

### Decision 4: 前端使用 Vite 而非 webpack

**选择**: Vite 作为前端构建工具

**理由**:
- 与 AGENTS.md 定义的技术栈一致
- 快速热更新，提升开发体验
- 简洁配置

### Decision 5: 后端使用 Spring Boot 3.x 标准项目结构

**选择**: 遵循 AGENTS.md 定义的包结构（controller/service/repository/entity/dto/exception）

**理由**:
- 与现有 specs 的分层架构（Controller -> Service -> Repository -> Entity）一致
- 团队成员容易理解项目结构
- 便于后续添加业务逻辑

## Risks / Trade-offs

| Risk | Description | Mitigation |
|------|-------------|------------|
| Docker Desktop 未安装 | 开发者机器可能未安装 Docker | README.md 中包含 Docker Desktop 安装说明 |
| 端口冲突 | 8080/5173/5432 端口可能被占用 | docker-compose.yml 和 .env 文件中明确端口配置 |
| JDK 版本不兼容 | Spring Boot 3.x 需要 JDK 17+ | pom.xml 中配置 maven.compiler.source/target |
| 前端依赖安装慢 | pnpm install 可能因网络问题失败 | 提供镜像源配置说明 |

## Migration Plan

**首次部署步骤**:
1. 安装 Docker Desktop 并启动
2. 执行 `docker compose up -d` 启动 PostgreSQL
3. 执行 `mvn spring-boot:run` 启动后端
4. 执行 `pnpm install && pnpm dev` 启动前端
5. 访问 `http://localhost:8080/api/v1/health` 验证后端
6. 访问 `http://localhost:5173` 验证前端

**回滚策略**: 本地开发环境无回滚需求，重启容器/进程即可。

## Open Questions

| Question | Status | Resolution |
|----------|--------|------------|
| 是否需要前端 Mock API Server？ | 已决定 | MVP 阶段不需要，后端 health 接口已足够 |
| 是否需要前端 router 配置？ | 已决定 | MVP 阶段使用简单欢迎页即可，router 在后续迭代添加 |
| 数据库初始化脚本是否需要？ | 已决定 | MVP 只需创建空库 visual_spider4，schema 由 JPA 自动生成 |
