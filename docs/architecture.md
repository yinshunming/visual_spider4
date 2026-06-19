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
│   ├── HealthController.java   # /api/v1/health
│   ├── PageFetchController.java        # /api/v1/page-fetch（M2 同步页面元信息抓取）
│   ├── BrowserSessionController.java   # /api/v1/browser/sessions（M2.5 单例 Playwright 会话）
│   ├── TaskController.java             # M4: /api/v1/tasks CRUD + stop
│   └── ArticleController.java          # M4: /api/v1/articles 分页/详情/导出
├── config/
│   ├── WebClientConfig.java    # HttpClient Bean（连接超时 8s，HTTP/1.1）
│   ├── PlaywrightConfig.java   # M2.5 Playwright.create() Bean，失败降级为 null
│   └── WebSocketConfig.java    # M2.5 注册 /api/v1/ws/page
├── service/                    # @Service，@Transactional
│   ├── CrawlConfigService.java
│   ├── CrawlFieldService.java
│   ├── HealthService.java
│   ├── PageFetchService.java   # M2 同步抓取：UrlGuard + httpClient + 大小限制
│   ├── UrlGuard.java           # M2 协议白名单 + 回环目标拦截（M4 也校验 crawl_config.startUrl）
│   ├── BrowserSessionService.java    # M2.5 Playwright 单会话生命周期
│   ├── SelectorCraftService.java     # M2.5 CSS/XPath 候选生成
│   ├── SelectorHighlighter.java      # M2.5 注入 .vs-highlight + 计数
│   ├── CssSelectorGenerator.java     # M2.5 自写（替代不可用的第三方库）
│   ├── XPathGenerator.java           # M2.5 Jsoup + 自写 XPath
│   ├── ExtractionService.java        # M3 批量提取 + 校验 + 字段级四态（M4 CrawlEngine 复用）
│   ├── FieldValueValidator.java      # M3 纯函数类型校验（TEXT/NUMBER/DATE/URL）
│   ├── CrawlEngine.java              # M4 进程内单任务锁 + LIST_DETAIL / DETAIL_ONLY 双流程调度
│   ├── CrawlTaskService.java         # M4 任务生命周期：createTask(DETAIL_ONLY urls→detail_url 落库) + stop
│   ├── ArticleQueryService.java      # M4 按 taskId / configId + keyword 查询 + 列集合聚合 + 导出
│   └── ZombieTaskCleanerRunner.java  # M4 启动时把 RUNNING 任务批量标 FAILED("服务重启,任务中断")
├── repository/                 # @Repository (Spring Data JPA)
│   ├── CrawlConfigRepository.java
│   ├── CrawlFieldRepository.java
│   ├── CrawlTaskRepository.java       # M4
│   ├── ListPageRepository.java        # M4
│   ├── ListItemRepository.java        # M4
│   ├── ArticleRepository.java         # M4（findByTaskId / findByConfigIdAndKeyword）
│   └── DetailUrlRepository.java       # M4
├── entity/                     # @Entity
│   ├── CrawlConfig.java        # M1 @OneToMany CrawlField (cascade ALL)；M4 增加 startUrl 字段（NOT NULL,UrlGuard 校验）
│   ├── CrawlField.java         # @ManyToOne CrawlConfig
│   ├── CrawlTask.java          # M4（@ManyToOne CrawlConfig；含 total/crawled/failed + 状态枚举）
│   ├── ListPage.java           # M4（@ManyToOne CrawlTask；raw_html 留待 M5 重新解析）
│   ├── ListItem.java           # M4（@ManyToOne ListPage；含 detail_url 字段，状态 + error_message）
│   ├── Article.java            # M4（@ManyToOne CrawlTask + CrawlConfig；listItemId 与 detailUrlId 二选一；custom_fields JSON）
│   └── DetailUrl.java          # M4（@ManyToOne CrawlTask；DETAIL_ONLY 任务的 URL 队列）
├── dto/
│   ├── ApiResponse.java        # {code, data, message} 统一包络
│   ├── HealthResponse.java
│   ├── request/
│   │   ├── CreateConfigRequest.java            # M4 起含 startUrl（@NotBlank）
│   │   ├── CreateFieldRequest.java
│   │   ├── UpdateConfigRequest.java            # M4 起含 startUrl
│   │   ├── PageFetchRequest.java               # M2: { url: string }
│   │   ├── OpenBrowserSessionRequest.java      # M2.5 显式空 record
│   │   └── CreateTaskRequest.java              # M4: { configId, urls }
│   ├── response/
│   │   ├── ConfigResponse.java                 # M4 起含 startUrl
│   │   ├── FieldResponse.java
│   │   ├── PageFetchResponse.java              # M2: { status, finalUrl, title, contentLength, fetchedAt }
│   │   ├── BrowserSessionResponse.java         # M2.5 { sessionId, status, currentUrl, createdAt }
│   │   ├── SelectorCandidate.java              # M2.5 { selector, matchCount, samples }
│   │   ├── SelectorPairResponse.java           # M2.5 { css, xpath }
│   │   ├── TaskResponse.java                   # M4 { id, configId, pageType, status, total/crawled/failed, started/completed, errorMessage }
│   │   ├── ArticleSummary.java                 # M4 { id, configId, url, status, customFields, errorMessage, fetchedAt }
│   │   └── ArticleDetail.java                  # M4 = ArticleSummary + raw_html 完整内容
│   └── ws/                                     # M2.5 + M3 WebSocket 消息 DTO
│       ├── WsMessage.java                      # 通用信封 { type, payload }
│       ├── LoadPagePayload.java                # { url, configId }
│       ├── ClickPayload.java                   # { x, y }
│       ├── PreviewPayload.java                 # { selectorType, selector }
│       ├── SaveFieldPayload.java               # { pageType, fieldName, fieldType, selector }
│       ├── ScreenshotPayload.java              # { data: base64 png }
│       ├── StatePayload.java                   # { state: LOADED|ERROR|CLOSED, message }
│       ├── PreviewResultPayload.java           # { matchCount, samples }
│       ├── SaveFieldResultPayload.java         # { ok, fieldId, message }
│       ├── PreviewTemplatePayload.java         # M3: { pageType: LIST|DETAIL }
│       ├── PreviewTemplateResultPayload.java   # M3: { result: { fields: [...], warnings: [...] } }
│       └── ErrorPayload.java                   # { code, message }
├── enums/
│   ├── PageType.java           # LIST_DETAIL, DETAIL_ONLY
│   ├── SelectorType.java       # CSS, XPATH
│   ├── FieldType.java          # TEXT, NUMBER, DATE, URL
│   ├── ConfigStatus.java       # ACTIVE, STOPPED（默认 STOPPED）
│   ├── FieldPageType.java      # LIST, DETAIL（字段属于哪个页面）
│   ├── PageFetchStatus.java    # M2: LOADING, SUCCESS, FAILED
│   ├── BrowserSessionStatus.java  # M2.5 ACTIVE, CLOSED
│   ├── TaskStatus.java         # M4: PENDING, RUNNING, COMPLETED, FAILED
│   ├── ItemStatus.java         # M4: list_item / article 通用 PENDING|CRAWLED|FAILED
│   └── DetailUrlStatus.java    # M4: DETAIL_ONLY detail_url PENDING|CRAWLED|FAILED
├── exception/
│   ├── BusinessException.java           # 基类，code + message
│   ├── ConfigNotFoundException.java     # 404 语义
│   ├── InvalidUrlException.java         # M2: code=4001
│   ├── BlockedAddressException.java     # M2: code=4003（同时被 M4 startUrl 校验复用）
│   ├── FetchTimeoutException.java       # M2: code=4004
│   ├── FetchFailedException.java        # M2: code=4002/4005
│   ├── BrowserSessionAlreadyActiveException.java  # M2.5 code=409
│   ├── BrowserSessionNotFoundException.java      # M2.5 code=404
│   ├── NavigationException.java                  # M2.5 code=4006
│   ├── StartUrlInvalidException.java             # M4 code=4007（startUrl 缺失/格式/回环）
│   ├── TaskAlreadyRunningException.java          # M4 code=4090（全局 RUNNING 锁被占）
│   ├── TaskNotFoundException.java                # M4 code=404
│   ├── ArticleNotFoundException.java             # M4 code=404
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice → ApiResponse
└── ws/                                     # M2.5 + M3 WebSocket 端点
    └── PageWebSocketHandler.java          # 处理 load/click/scroll/preview/saveField/close/previewTemplate 七种消息
```

### 数据模型

```sql
-- M1 已创建
crawl_config (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR NOT NULL,
  start_url       VARCHAR(2048) NOT NULL,  -- M4：爬取起始 URL（UrlGuard 校验 http(s) + 非回环）
  page_type       VARCHAR(20) NOT NULL,    -- LIST_DETAIL | DETAIL_ONLY
  selector_type   VARCHAR(20) NOT NULL,    -- CSS | XPATH
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

-- M4 五张表（已建，由 ddl-auto: update 自动建/改）

crawl_task (
  id              BIGSERIAL PRIMARY KEY,
  config_id       BIGINT NOT NULL REFERENCES crawl_config(id) ON DELETE CASCADE,
  page_type       VARCHAR(20) NOT NULL,         -- 与 config.page_type 一致
  status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',  -- PENDING|RUNNING|COMPLETED|FAILED
  total_items     INT NOT NULL DEFAULT 0,
  crawled_items   INT NOT NULL DEFAULT 0,
  failed_items    INT NOT NULL DEFAULT 0,
  started_at      TIMESTAMP NOT NULL,
  completed_at    TIMESTAMP,
  error_message   TEXT
);

list_page (    -- 每个访问的列表页一条；M5 用于重解析
  id          BIGSERIAL PRIMARY KEY,
  task_id     BIGINT NOT NULL REFERENCES crawl_task(id) ON DELETE CASCADE,
  config_id   BIGINT NOT NULL REFERENCES crawl_config(id) ON DELETE CASCADE,
  url         VARCHAR(2048) NOT NULL,
  raw_html    TEXT NOT NULL,                  -- 完整 HTML，留 M5 重新解析
  fetched_at  TIMESTAMP NOT NULL
);

list_item (   -- 列表页解析出的每个列表项
  id            BIGSERIAL PRIMARY KEY,
  list_page_id  BIGINT NOT NULL REFERENCES list_page(id) ON DELETE CASCADE,
  detail_url    VARCHAR(2048) NOT NULL,
  status        VARCHAR(20) NOT NULL,        -- PENDING|CRAWLED|FAILED
  error_message TEXT
);

article (     -- 每个访问的详情页一条；custom_fields JSON
  id              BIGSERIAL PRIMARY KEY,
  config_id       BIGINT NOT NULL REFERENCES crawl_config(id) ON DELETE CASCADE,
  task_id         BIGINT NOT NULL REFERENCES crawl_task(id) ON DELETE CASCADE,
  list_item_id    BIGINT REFERENCES list_item(id),     -- LIST_DETAIL 模式必填
  detail_url_id   BIGINT REFERENCES detail_url(id),    -- DETAIL_ONLY 模式必填
  url             VARCHAR(2048) NOT NULL,
  raw_html        TEXT NOT NULL,
  custom_fields   TEXT NOT NULL DEFAULT '{}',         -- JSON 文本，M5 可换 jsonb
  status          VARCHAR(20) NOT NULL,               -- PENDING|CRAWLED|FAILED
  error_message   TEXT,
  fetched_at      TIMESTAMP NOT NULL
);

detail_url (  -- DETAIL_ONLY 模式用户提供的 URL；详情抽取前每 URL 一条 PENDING
  id            BIGSERIAL PRIMARY KEY,
  task_id       BIGINT NOT NULL REFERENCES crawl_task(id) ON DELETE CASCADE,
  url           VARCHAR(2048) NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|CRAWLED|FAILED
  error_message TEXT
);
```

> 级联关系：`DELETE /api/v1/configs/{id}` 通过 JPA cascade 清 `crawl_field` + 任务下全部爬取产物。`DELETE /api/v1/tasks/{id}` 清该任务下 `list_page` / `list_item` / `article` / `detail_url`。
> 数据流与设计取舍的完整真相源：[openspec/specs/crawl-execution/spec.md](../openspec/specs/crawl-execution/spec.md) 与 [openspec/specs/data-persistence/spec.md](../openspec/specs/data-persistence/spec.md)。

### 关键设计决策

| 决策 | 理由 |
|------|------|
| 字段作为子资源 `/configs/{id}/fields` | 字段必须依附配置存在，无独立意义 |
| PUT 配置时 fields[] 全量替换 | 简化前端，无需 diff；配置快照语义 |
| 删除走 `service.delete(entity)` | `repository.deleteById(id)` 跳过 JPA cascade，会触发外键违反 |
| JPA `@OneToMany` + `orphanRemoval=true` | 删除配置时级联清理字段 |
| `ApiResponse` 统一包络 | 前端无需为每条响应解析不同形状；HTTP 状态码统一为 200 |
| 状态默认 `STOPPED` | 新建配置不会自动启动爬取 |
| `crawl_config.start_url` M4 起必填（UrlGuard 校验） | LIST_DETAIL 入口页 / DETAIL_ONLY 默认详情页源；DETAIL_ONLY 任务实际 URL 仍由创建任务时提供的 `urls[]` 决定 |
| `CrawlEngine` 进程内单任务锁 | MVP 不引入 Redis/JVM 级锁；单机够用且实现简单（AtomicReference + AtomicBoolean） |
| DETAIL_ONLY 任务凭 `urls[]` 落 `detail_url` 记录（M4） | 任务可暂停后用同 URL 集合重跑，便于重试；与 LIST_DETAIL 流程解耦 |

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
│   ├── config.js            # config + field CRUD 9 个方法
│   ├── pageFetch.js         # M2: fetchPage({ url })
│   ├── browser.js           # M2.5: openSession / closeSession / getStatus / connectWs(onMessage) + M3: sendPreviewTemplate / onPreviewTemplateResult
│   ├── tasks.js             # M4: listTasks / getTask / createTask / stopTask / deleteTask
│   └── articles.js          # M4: listArticles (支持 taskId 优先) / getArticle / exportArticles
├── stores/
│   ├── configStore.js       # useConfigStore: list / current / loading / error + actions
│   ├── pageFetchStore.js    # M2: usePageFetchStore: status / lastResult / lastError + fetch()
│   ├── browserSessionStore.js  # M2.5: useBrowserSessionStore: status / lastScreenshot / selectors / previewResult / saveFieldResult + loadUrl/click/preview/saveField
│   ├── extractionPreviewStore.js  # M3: useExtractionPreviewStore: results/warnings per pageType + triggerPreview/getResult/getWarnings
│   ├── taskStore.js         # M4: useTaskStore: list / total / current / isLoading / _pollTimer + fetchList / startPolling / createAndPoll
│   └── articleStore.js      # M4: useArticleStore: list / total / taskId / configId + fetchList / fetchOne / exportFile
├── router/
│   └── index.js             # / → /configs, /configs, /configs/new, /configs/:id, /configs/:id/preview, /tasks, /tasks/:id
└── views/
    ├── ConfigList.vue       # 列表 + 新建/编辑/删除/预览/启动爬取按钮 + 分页（DETAIL_ONLY 启动弹框）
    ├── ConfigEdit.vue       # 新建/编辑双模式 + startUrl 输入 + 字段动态增删 + 打开预览入口
    ├── PagePreview.vue      # M2.5 + M3: el-tabs 容器，Tab1=造字段，Tab2=按模板预览
    ├── WelcomePage.vue      # 首页（重定向入口 + 健康检查）
    ├── TaskList.vue         # M4: 任务分页 + configId 过滤 + 进度展示（COMPLETED 显示 100% + success）
    ├── TaskDetail.vue       # M4: 任务状态 + 进度 + 该任务的爬取条目（按 taskId 拉 articles）
    └── StartCrawlDialog.vue # M4: DETAIL_ONLY 启动爬取弹框（每行一个 URL，空列表拒绝）
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

| 能力 | 状态 | 涉及层 |
|------|------|--------|
| `page-visual-selection`（HTTP 同步加载 MVP 切片） | ✅ M2 完成 | `controller/PageFetchController` + `service/PageFetchService` + `service/UrlGuard` + `config/WebClientConfig` + 前端 `views/PagePreview.vue` + `stores/pageFetchStore.js` |
| `page-visual-selection`（Playwright + WebSocket 端到端） | ✅ M2.5 完成（`visual-selector-craft` change） | `controller/BrowserSessionController` + `service/BrowserSessionService` + `service/SelectorCraftService` + `service/SelectorHighlighter` + `ws/PageWebSocketHandler` + `config/PlaywrightConfig` + `config/WebSocketConfig` + 前端 `api/browser.js` + `stores/browserSessionStore.js` |
| `selector-rule-management` | ⬜ 未开始 | 扩展 `CrawlField`，新增 detail_url 必填校验 |
| `extraction-template` | ✅ M3 完成（`implement-extraction-template-preview` change） | 后端 `service/ExtractionService` + `service/FieldValueValidator` + `enums/FieldPreviewStatus` + DTO `FieldPreviewResult` / `ExtractionPreviewResponse` / `PreviewTemplatePayload` / `PreviewTemplateResultPayload` + 改造 `ws/PageWebSocketHandler`；前端 `api/browser.js` 新增 `sendPreviewTemplate` / `onPreviewTemplateResult` + `stores/extractionPreviewStore.js` + `views/PagePreview.vue` 引入 Tab 容器 |
| `extraction-preview-validation` | ✅ M3 完成 | 字段级四态（OK/TYPE_MISMATCH/NO_MATCH/SELECTOR_INVALID）+ 软警告（detail_url 缺失、空模板）|
| `crawl-execution` | ✅ M4 完成（`implement-manual-crawl-execution` change） | 后端 `service/CrawlEngine`（进程内单任务锁 AtomicReference + AtomicBoolean stopFlag） + `service/CrawlTaskService` + `service/ZombieTaskCleanerRunner` + 5 张新表 + 3 个新枚举；前端 `api/tasks.js` + `stores/taskStore` + `views/TaskList` / `TaskDetail` / `StartCrawlDialog`；DETAIL_ONLY 弹框收 URL；全局 4090 错误码 |
| `data-persistence` | ✅ M4 完成 | 5 张表（`crawl_task` / `list_page` / `list_item` / `article` / `detail_url`）+ `service/ArticleQueryService`（按 taskId/configId + keyword 查询、列集合聚合、JSON/xlsx 导出）+ `controller/ArticleController` + `controller/TaskController`；前端 `api/articles.js` + `stores/articleStore` |

## M3 按模板预览流程

```
[前端 PagePreview Tab2]
  用户点击"按当前模板预览"
        |
        v
  extractionPreviewStore.triggerPreview(pageType)
        |  isLoading=true
        |  ws.send({type:"previewTemplate", payload:{pageType}})
        v
[WS /api/v1/ws/page]
  PageWebSocketHandler.handlePreviewTemplate
        |  1. 校验 BrowserSessionService.getPage() != null → 否则 error NO_SESSION
        |  2. 校验 sessionToConfig.get(sessionId) 非空 → 否则 error BAD_REQUEST
        |  3. 反序列化 PreviewTemplatePayload
        v
  ExtractionService.extractByTemplate(page, configId, pageType)
        |  ├─ CrawlConfigService.getById(configId) → config
        |  ├─ 过滤 pageType 字段，按 createdAt ASC 排序
        |  ├─ 空模板 → warnings += "该模板未定义任何 <pageType> 字段"
        |  ├─ LIST_DETAIL + LIST + 缺 detail_url → warnings += detail_url 文案
        |  └─ 对每个字段:
        |       page.evaluate("(sel) => ...", selector)  // 单脚本批量抽取
        |       ├─ 抛错 → SELECTOR_INVALID + message
        |       ├─ 返回 [] → NO_MATCH
        |       └─ 返回 [v1, v2, ...] → 逐项 FieldValueValidator.validate
        |           ├─ 任一为 null → TYPE_MISMATCH + message
        |           └─ 全部合法 → OK
        v
  WsMessage<PreviewTemplateResultPayload> 发回前端
        |
        v
[前端 extractionPreviewStore._onMessage]
  写入 results[pageType] / warnings[pageType]
  isLoading=false
  render Table (el-tag 状态徽章 + el-alert 警告横幅)
```

**关键边界**：
- `ExtractionService` 设计为 `Page + configId + pageType` 入参 + 纯返回 `ExtractionPreviewResponse`，**不依赖 WebSocket**。M4 `crawl-execution` 阶段在循环里直接调用即可，无需重写
- `FieldValueValidator` 是无 Spring 依赖的纯函数，可在任何上下文（含脚本工具）独立验证
- URL 字段优先取 `element.href`（浏览器自动绝对化），非链接元素退回 `textContent`，由 `ExtractionService` 的 page.evaluate 脚本统一处理
- 单脚本批量抽取（同一 selector 一次 evaluate 拿 N 个值），比"一字段一 evaluate"快 N 倍
