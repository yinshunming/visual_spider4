# 架构

## 总览

视觉爬虫采用前后端分离架构。前端 Vue 3 + Element Plus 负责 UI，后端 Spring Boot 3 + JPA 负责业务逻辑和持久化，前端通过 Vite 代理调用后端 REST API。

```
浏览器 (Vue 3 SPA)
   |
   |  HTTP /api/v1/*  (Vite dev proxy → http://localhost:8080)
   v
Spring Boot 3.2.5
   |
   v
PostgreSQL 16
```

## 后端架构

### 分层

```
Controller  (REST 边界，参数接收/响应转换)
   |
Service     (业务规则，事务边界)
   |
Repository  (JPA 仓储，查询抽象)
   |
Entity      (JPA 实体，领域模型)
```

### 包结构

```
com.visualspider/
├── Application.java            # @SpringBootApplication 入口
├── controller/                 # @RestController
│   ├── ConfigController.java   # /api/v1/configs CRUD
│   ├── FieldController.java    # /api/v1/configs/{id}/fields + /api/v1/fields/{id}
│   └── HealthController.java   # /api/v1/health
├── service/                    # @Service，@Transactional
│   ├── CrawlConfigService.java
│   ├── CrawlFieldService.java
│   └── HealthService.java
├── repository/                 # @Repository (Spring Data JPA)
│   ├── CrawlConfigRepository.java
│   └── CrawlFieldRepository.java
├── entity/                     # @Entity
│   ├── CrawlConfig.java        # @OneToMany CrawlField (cascade ALL)
│   └── CrawlField.java         # @ManyToOne CrawlConfig
├── dto/
│   ├── ApiResponse.java        # {code, data, message} 统一包络
│   ├── HealthResponse.java
│   ├── request/
│   │   ├── CreateConfigRequest.java
│   │   ├── CreateFieldRequest.java
│   │   └── UpdateConfigRequest.java   # 含 fields[] 列表
│   └── response/
│       ├── ConfigResponse.java        # 含 fields
│       └── FieldResponse.java
├── enums/
│   ├── PageType.java           # LIST_DETAIL, DETAIL_ONLY
│   ├── SelectorType.java       # CSS, XPATH
│   ├── FieldType.java          # TEXT, NUMBER, DATE, URL
│   ├── ConfigStatus.java       # ACTIVE, STOPPED（默认 STOPPED）
│   └── FieldPageType.java      # LIST, DETAIL（字段属于哪个页面）
└── exception/
    ├── BusinessException.java           # 基类，code + message
    ├── ConfigNotFoundException.java     # 404 语义
    └── GlobalExceptionHandler.java      # @RestControllerAdvice → ApiResponse
```

### 数据模型

```sql
-- M1 已创建
crawl_config (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR NOT NULL,
  page_type       VARCHAR(20) NOT NULL,  -- LIST_DETAIL | DETAIL_ONLY
  selector_type   VARCHAR(20) NOT NULL,  -- CSS | XPATH
  status          VARCHAR(20) NOT NULL DEFAULT 'STOPPED',
  created_at      TIMESTAMP NOT NULL,
  updated_at      TIMESTAMP NOT NULL
);

crawl_field (
  id            BIGSERIAL PRIMARY KEY,
  config_id     BIGINT NOT NULL REFERENCES crawl_config(id) ON DELETE CASCADE,
  page_type     VARCHAR(20) NOT NULL,    -- LIST | DETAIL
  field_name    VARCHAR NOT NULL,
  field_type    VARCHAR(20) NOT NULL,    -- TEXT | NUMBER | DATE | URL
  selector      VARCHAR NOT NULL,
  created_at    TIMESTAMP NOT NULL,
  updated_at    TIMESTAMP NOT NULL
);
```

> M2+ 还将新增：`crawl_task`、`list_page`、`list_item`、`article`、`detail_url`（参考 [openspec/specs/data-persistence/spec.md](../openspec/specs/data-persistence/spec.md)）。

### 关键设计决策

| 决策 | 理由 |
|------|------|
| 字段作为子资源 `/configs/{id}/fields` | 字段必须依附配置存在，无独立意义 |
| PUT 配置时 fields[] 全量替换 | 简化前端，无需 diff；配置快照语义 |
| 删除走 `service.delete(entity)` | `repository.deleteById(id)` 跳过 JPA cascade，会触发外键违反 |
| JPA `@OneToMany` + `orphanRemoval=true` | 删除配置时级联清理字段 |
| `ApiResponse` 统一包络 | 前端无需为每条响应解析不同形状；HTTP 状态码统一为 200 |
| 状态默认 `STOPPED` | 新建配置不会自动启动爬取 |

## 前端架构

### 分层

```
App.vue (路由出口)
  |
  v
Router (vue-router) → View
  |
  v
View (Vue 3 SFC)
  |
  v
Pinia Store (useConfigStore)
  |
  v
API Module (api/config.js → Axios)
  |
  v
后端 (Vite 代理 → /api/v1)
```

### 目录

```
frontend/src/
├── main.js                  # 创建 app、注册 Pinia / Router / ElementPlus
├── App.vue                  # 路由出口 <router-view />
├── api/
│   ├── index.js             # 公共 axios 实例 (baseURL: /api/v1)
│   ├── health.js            # 健康检查（历史遗留，WelcomePage 仍在用）
│   └── config.js            # config + field CRUD 9 个方法
├── stores/
│   └── configStore.js       # useConfigStore: list / current / loading / error + actions
├── router/
│   └── index.js             # / → /configs, /configs, /configs/new, /configs/:id
└── views/
    ├── ConfigList.vue       # 列表 + 新建/编辑/删除按钮 + 分页
    └── ConfigEdit.vue       # 新建/编辑双模式 + 字段动态增删
```

### 关键设计决策

| 决策 | 理由 |
|------|------|
| 详情页与编辑页合一 (`/configs/:id`) | 减少重复代码；同一组件根据 `route.params.id` 切换模式 |
| Vite 代理 `/api` → `localhost:8080` | 开发期跨域透明；生产环境反代同样路径 |
| Store 暴露 `loading` / `error` 状态 | View 层可以无脑 v-loading + ElMessage 错误处理 |
| `UpdateConfigRequest` 在前端一次性 PUT | 服务端全量替换字段，避免前端做 diff |

## 后续里程碑预览

M2+ 计划新增（参考 [openspec/specs/](../openspec/specs/)）：

| 能力 | 涉及层 |
|------|--------|
| `page-visual-selection` | 新增 `websocket/` 包（推送截图帧）、前端 Playwright 控制 UI |
| `selector-rule-management` | 扩展 `CrawlField`，新增 detail_url 必填校验 |
| `extraction-template` | 新增 `service/Extractor.java`，复用 Playwright + Jsoup |
| `crawl-execution` | 新增 `service/CrawlEngine.java`，ThreadPoolExecutor 控制并发 |
| `data-persistence` | 新增 `list_page` / `list_item` / `article` / `crawl_task` 实体 |
