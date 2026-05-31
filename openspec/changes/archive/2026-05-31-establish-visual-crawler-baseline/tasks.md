## 1. 数据库环境搭建

- [x] 1.1 创建 docker-compose.yml，配置 PostgreSQL 服务（visual_spider4 数据库，postgres/123456）
- [x] 1.2 验证 PostgreSQL 容器启动成功且数据库可访问

## 2. 后端项目初始化

- [x] 2.1 创建后端 Maven 项目，包含 pom.xml（Spring Boot 3.x、Java 21+、web、data-jpa、postgresql）
- [x] 2.2 创建包结构：com.visualspider 及 controller/service/repository/entity/dto/exception 子包
- [x] 2.3 创建 application.yml，配置数据库连接（localhost:5432/visual_spider4，postgres/123456）
- [x] 2.4 创建 Application 主类
- [x] 2.5 验证 `mvn clean compile` 编译成功

## 3. 后端 - 健康检查端点（TDD）

### RED 阶段 - 先写测试
- [x] 3.1 编写 HealthControllerTest：GET /api/v1/health 返回 200，状态为 UP
- [x] 3.2 编写 HealthControllerTest：响应包含 code、data（含 status/database/timestamp）、message 字段
- [x] 3.3 编写 HealthControllerTest：数据库不可用时，data.database 为 DOWN
- [x] 3.4 运行测试，验证测试失败（功能尚未实现）

### GREEN 阶段 - 最小化实现
- [x] 3.5 实现 ApiResponse DTO 类（code、data、message）
- [x] 3.6 实现 HealthResponse DTO（status、database、timestamp）
- [x] 3.7 实现 HealthController，包含 GET /api/v1/health 端点
- [x] 3.8 实现 HealthService，包含数据库连接检查
- [x] 3.9 运行测试，验证测试通过

### REFACTOR 阶段
- [x] 3.10 为 HealthService 添加 @Slf4j 日志
- [x] 3.11 验证所有测试仍然通过

## 4. 后端 - 统一响应格式（TDD）

### RED 阶段 - 先写测试
- [x] 4.1 编写 GlobalExceptionHandlerTest：任何异常返回正确的错误响应格式
- [x] 4.2 编写 BusinessExceptionTest：自定义异常返回正确结构
- [x] 4.3 运行测试，验证测试失败

### GREEN 阶段 - 最小化实现
- [x] 4.4 创建 BusinessException 类（包含 code 和 message）
- [x] 4.5 创建 GlobalExceptionHandler，使用 @ControllerAdvice
- [x] 4.6 实现异常处理，返回统一响应格式
- [x] 4.7 运行测试，验证测试通过

## 5. 前端项目初始化

- [x] 5.1 使用 Vite 创建 Vue 3 前端项目
- [x] 5.2 安装依赖：vue、vite、@vitejs/plugin-vue、element-plus、axios、pinia
- [x] 5.3 创建 src/main.js，挂载 Vue 应用
- [x] 5.4 创建 src/App.vue，包含基础结构
- [x] 5.5 创建 index.html 入口文件
- [x] 5.6 配置 vite.config.js，代理到后端（localhost:8080/api）
- [x] 5.7 验证 `pnpm install` 安装成功
- [x] 5.8 验证 `pnpm build` 构建成功

## 6. 前端 - 欢迎页（TDD）

### RED 阶段 - 先写测试
- [x] 6.1 编写 WelcomePage.spec.js：访问 / 时，页面显示欢迎内容
- [x] 6.2 编写 WelcomePage.spec.js：后端健康时，显示健康状态
- [x] 6.3 运行测试，验证测试失败

### GREEN 阶段 - 最小化实现
- [x] 6.4 创建 src/views/WelcomePage.vue，显示欢迎信息
- [x] 6.5 创建 src/api/health.js，使用 axios 调用 /api/v1/health
- [x] 6.6 实现欢迎页，调用健康检查 API 并展示状态
- [x] 6.7 运行测试，验证测试通过

## 7. 前端 - API 封装（TDD）

### RED 阶段 - 先写测试
- [x] 7.1 编写 api.spec.js：axios 实例 baseURL 设置为 /api/v1
- [x] 7.2 编写 api.spec.js：请求/响应拦截器正常工作
- [x] 7.3 运行测试，验证测试失败

### GREEN 阶段 - 最小化实现
- [x] 7.4 创建 src/api/index.js，配置 axios 实例
- [x] 7.5 添加响应拦截器，处理统一响应格式
- [x] 7.6 运行测试，验证测试通过

## 8. 集成验证

- [x] 8.1 启动 Docker PostgreSQL 容器
- [x] 8.2 启动后端 `mvn spring-boot:run`，验证健康检查端点 http://localhost:8080/api/v1/health
- [x] 8.3 启动前端 `pnpm dev`，验证欢迎页 http://localhost:5173
- [x] 8.4 端到端验证：前端通过代理调用后端健康检查 API

## 9. 文档

- [x] 9.1 创建 README.md，包含本地开发环境搭建说明
- [x] 9.2 记录环境变量（DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD）
- [x] 9.3 记录启动命令：docker compose up、mvn spring-boot:run、pnpm dev
- [x] 9.4 记录端口映射：8080（后端）、5173（前端）、5432（PostgreSQL）
