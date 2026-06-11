# Visual Spider - 可视化爬虫管理系统

## 当前进度

| 里程碑 | 状态 |
|--------|------|
| M0 开发环境 | ✅ 完成 |
| M1 项目管理 | ✅ 完成 |
| M2 页面可视化（HTTP 同步切片） | ✅ 完成 |
| M2.5 页面可视化（Playwright + WebSocket 端到端） | ✅ 完成 |
| 选择器/抽取/爬取 | ⬜ 未开始 |

详见 [AGENTS.md](AGENTS.md) §3 与 [openspec/specs/](openspec/specs/)。测试统计见 [docs/tdd-guide.md](docs/tdd-guide.md) §测试统计。

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **语言**: Java 21
- **ORM**: Spring Data JPA
- **数据库**: PostgreSQL 16
- **构建工具**: Maven

### 前端
- **框架**: Vue 3
- **构建工具**: Vite 5
- **UI 组件**: Element Plus
- **状态管理**: Pinia
- **路由**: vue-router
- **HTTP 客户端**: Axios
- **测试**: vitest + @vue/test-utils（已配置，本里程碑未写测试）

## 本地开发环境搭建

### 前置条件

- JDK 21+
- Node.js 18+
- PostgreSQL 16+（本机服务，默认端口 5432，库 `visual_spider4`，用户 `postgres`，密码 `123456`）

### 1. 启动数据库

启动本机 PostgreSQL 服务（任选其一）：

- **Windows**：在"服务"中启动 `postgresql-x64-16`（EDB 安装器默认会注册为服务）
- **macOS**：`brew services start postgresql@16`
- **Linux**：`sudo systemctl start postgresql`

验证 PostgreSQL 可达：
```bash
pg_isready -h localhost -p 5432
# 期望输出：localhost:5432 - accepting connections
```

> 本项目**不依赖 Docker Desktop**。如果之前用过 `docker-compose.yml` 启动容器，请改用本机 PG 服务，详见 `docs/runbook.md` §PostgreSQL。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动成功后：
- 健康检查：`http://localhost:8080/api/v1/health`
- 配置管理 API：`http://localhost:8080/api/v1/configs`

### 3. 启动前端

```bash
cd frontend
npm install  # 首次运行
npm run dev
```

前端启动成功后访问：`http://localhost:5173`（已配 Vite 代理 `/api` → `http://localhost:8080`）

## 环境变量

后端支持以下环境变量（可选，使用默认值即可本地开发）：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | 数据库主机 |
| DB_PORT | 5432 | 数据库端口 |
| DB_NAME | visual_spider4 | 数据库名称 |
| DB_USERNAME | postgres | 数据库用户名 |
| DB_PASSWORD | 123456 | 数据库密码 |

## 端口映射

| 服务 | 端口 |
|------|------|
| 后端 (Spring Boot) | 8080 |
| 前端 (Vite) | 5173 |
| PostgreSQL | 5432 |

## 项目结构

```
visual_spider4/
├── backend/                 # Spring Boot 3.2.5 + JPA + Java 21
│   ├── src/main/java/com/visualspider/
│   │   ├── Application.java
│   │   ├── config/          # WebClientConfig / PlaywrightConfig / WebSocketConfig
│   │   ├── controller/      # Config / Field / Health / PageFetch / BrowserSession
│   │   ├── service/         # Config / Field / Health / PageFetch / UrlGuard /
│   │   │                    # BrowserSession / SelectorCraft / SelectorHighlighter /
│   │   │                    # CssSelectorGenerator / XPathGenerator
│   │   ├── repository/      # JPA 仓库
│   │   ├── entity/          # JPA 实体
│   │   ├── dto/             # request/ response/ ws/ 三类
│   │   ├── enums/           # 含 BrowserSessionStatus
│   │   ├── exception/       # 含 BrowserSession* / NavigationException
│   │   └── ws/              # PageWebSocketHandler
│   ├── src/test/            # 101 个测试
│   └── pom.xml
├── frontend/                # Vue 3 + Vite + Element Plus + Pinia
│   ├── src/
│   │   ├── api/             # Axios 封装（index/health/config/pageFetch/browser）
│   │   ├── stores/          # Pinia store（configStore/pageFetchStore/browserSessionStore）
│   │   ├── views/           # ConfigList / ConfigEdit / PagePreview
│   │   ├── router/          # vue-router 配置
│   │   ├── App.vue
│   │   └── main.js
│   ├── vite.config.js
│   └── package.json
├── e2e/                     # Playwright E2E 测试（真 Chromium 跑 PagePreview 全链路）
│   ├── tests/               # page-preview.spec.js
│   ├── scripts/             # start-stack.js
│   ├── playwright.config.js
│   ├── package.json
│   └── README.md
├── openspec/                # OpenSpec 规格
│   ├── specs/               # 9 个 capability 真相源
│   └── changes/             # 活跃 + archive/
├── docs/                    # 深入文档
│   ├── architecture.md      # 架构
│   ├── api-guide.md         # API 参考
│   ├── runbook.md            # 运维 + Known Issues
│   ├── tdd-guide.md          # TDD 模板 + 测试统计
│   └── explore/              # 历史设计探索
├── AGENTS.md                # 项目规则（AI 必读）
└── README.md
```

## API 速查

所有 API 统一响应包络：

```json
// 成功
{ "code": 200, "data": {...}, "message": "success" }

// 错误（HTTP 状态码仍是 200）
{ "code": 404, "data": null, "message": "CrawlConfig not found: id=99" }
```

### 配置（M1 已实现）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/configs` | 分页查询（参数 `page`、`size`） |
| POST | `/api/v1/configs` | 创建配置（status 默认 STOPPED） |
| GET | `/api/v1/configs/{id}` | 获取详情（带字段） |
| PUT | `/api/v1/configs/{id}` | 更新配置（`fields[]` 全量替换） |
| DELETE | `/api/v1/configs/{id}` | 删除配置（级联删除字段） |

### 字段（子资源，M1 已实现）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/configs/{configId}/fields` | 列出字段 |
| POST | `/api/v1/configs/{configId}/fields` | 添加字段 |
| PUT | `/api/v1/fields/{id}` | 更新字段 |
| DELETE | `/api/v1/fields/{id}` | 删除字段 |

### 健康检查

```
GET /api/v1/health
```

响应示例：
```json
{
  "code": 200,
  "data": { "status": "UP", "database": "UP", "timestamp": "2026-06-01T00:00:00Z" },
  "message": "success"
}
```

### 页面加载（M2 同步加载 MVP）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/page-fetch` | 同步抓取目标页面元信息（title / finalUrl / contentLength），返回 HTTP 200/4xx/5xx + `code` 双层语义 |

```bash
curl -X POST http://localhost:8080/api/v1/page-fetch \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

详细 API 文档（请求/响应示例、错误码）：[docs/api-guide.md](docs/api-guide.md)。

## 常用命令

### 后端
```bash
cd backend
mvn clean compile        # 编译
mvn test                 # 跑所有测试（101 项）
mvn clean package -DskipTests  # 打 jar（推荐，绕过 Lombok 增量编译）
java -jar target/visual-spider-backend-0.0.1-SNAPSHOT.jar  # 启动
# 或：mvn spring-boot:run      # 增量编译启动（注意 Lombok 陷阱）
```

### 前端
```bash
cd frontend
npm install              # 首次安装
npm run dev              # 开发服务器
npm run build            # 生产构建
npm run preview          # 预览构建
npm test                 # 跑 vitest（17 项）
```

### 端到端（真 Chromium，需本机 PG + Playwright Chromium 已装）
```bash
cd e2e
npm install
npm run install-browser  # 装 Playwright Chromium（仅首次）
npm test                 # 自动拉 jar + vite dev + 跑测试 + 关进程
```

## 深入阅读

- [AGENTS.md](AGENTS.md) — 项目红线与约定
- [docs/architecture.md](docs/architecture.md) — 架构与数据流
- [docs/api-guide.md](docs/api-guide.md) — 完整 API 参考
- [docs/runbook.md](docs/runbook.md) — 启动/测试/故障排查/Known Issues
- [docs/tdd-guide.md](docs/tdd-guide.md) — TDD 模板与反模式
- [e2e/README.md](e2e/README.md) — 端到端测试前置与踩坑
- [openspec/specs/](openspec/specs/) — 9 个 capability 真相源
