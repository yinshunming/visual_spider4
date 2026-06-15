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
│   └── BrowserSessionController.java   # /api/v1/browser/sessions（M2.5 单例 Playwright 会话）
├── config/
│   ├── WebClientConfig.java    # HttpClient Bean（连接超时 8s，HTTP/1.1）
│   ├── PlaywrightConfig.java   # M2.5 Playwright.create() Bean，失败降级为 null
│   └── WebSocketConfig.java    # M2.5 注册 /api/v1/ws/page
├── service/                    # @Service，@Transactional
│   ├── CrawlConfigService.java
│   ├── CrawlFieldService.java
│   ├── HealthService.java
│   ├── PageFetchService.java   # M2 同步抓取：UrlGuard + httpClient + 大小限制
│   ├── UrlGuard.java           # M2 协议白名单 + 回环目标拦截
│   ├── BrowserSessionService.java    # M2.5 Playwright 单会话生命周期
│   ├── SelectorCraftService.java     # M2.5 CSS/XPath 候选生成
│   ├── SelectorHighlighter.java      # M2.5 注入 .vs-highlight + 计数
│   ├── CssSelectorGenerator.java     # M2.5 自写（替代不可用的第三方库）
│   ├── XPathGenerator.java           # M2.5 Jsoup + 自写 XPath
│   ├── ExtractionService.java        # M3 批量提取 + 校验 + 字段级四态
│   └── FieldValueValidator.java      # M3 纯函数类型校验（TEXT/NUMBER/DATE/URL）
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
│   │   ├── UpdateConfigRequest.java            # 含 fields[] 列表
│   │   ├── PageFetchRequest.java               # M2: { url: string }
│   │   └── OpenBrowserSessionRequest.java      # M2.5 显式空 record
│   ├── response/
│   │   ├── ConfigResponse.java                 # 含 fields
│   │   ├── FieldResponse.java
│   │   ├── PageFetchResponse.java              # M2: { status, finalUrl, title, contentLength, fetchedAt }
│   │   ├── BrowserSessionResponse.java         # M2.5 { sessionId, status, currentUrl, createdAt }
│   │   ├── SelectorCandidate.java              # M2.5 { selector, matchCount, samples }
│   │   └── SelectorPairResponse.java           # M2.5 { css, xpath }
│   └── ws/                                     # M2.5 WebSocket 消息 DTO
│       ├── WsMessage.java                      # 通用信封 { type, payload }
│       ├── LoadPagePayload.java                # { url, configId }
│       ├── ClickPayload.java                   # { x, y }
│       ├── PreviewPayload.java                 # { selectorType, selector }
│       ├── SaveFieldPayload.java               # { pageType, fieldName, fieldType, selector }
│       ├── ScreenshotPayload.java              # { data: base64 png }
│       ├── StatePayload.java                   # { state: LOADED|ERROR|CLOSED, message }
│       ├── PreviewResultPayload.java           # { matchCount, samples }
│       ├── SaveFieldResultPayload.java         # { ok, fieldId, message }
│       └── ErrorPayload.java                   # { code, message }
├── enums/
│   ├── PageType.java           # LIST_DETAIL, DETAIL_ONLY
│   ├── SelectorType.java       # CSS, XPATH
│   ├── FieldType.java          # TEXT, NUMBER, DATE, URL
│   ├── ConfigStatus.java       # ACTIVE, STOPPED（默认 STOPPED）
│   ├── FieldPageType.java      # LIST, DETAIL（字段属于哪个页面）
│   ├── PageFetchStatus.java    # M2: LOADING, SUCCESS, FAILED
│   └── BrowserSessionStatus.java  # M2.5 ACTIVE, CLOSED
├── exception/
│   ├── BusinessException.java           # 基类，code + message
│   ├── ConfigNotFoundException.java     # 404 语义
│   ├── InvalidUrlException.java         # M2: code=4001
│   ├── BlockedAddressException.java     # M2: code=4003
│   ├── FetchTimeoutException.java       # M2: code=4004
│   ├── FetchFailedException.java        # M2: code=4002/4005
│   ├── BrowserSessionAlreadyActiveException.java  # M2.5 code=409
│   ├── BrowserSessionNotFoundException.java      # M2.5 code=404
│   ├── NavigationException.java                  # M2.5 code=4006
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice → ApiResponse
└── ws/                                     # M2.5 WebSocket 端点
    └── PageWebSocketHandler.java          # 处理 load/click/preview/saveField/close 五种消息
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
│   ├── config.js            # config + field CRUD 9 个方法
│   ├── pageFetch.js         # M2: fetchPage({ url })
│   └── browser.js           # M2.5: openSession / closeSession / getStatus / connectWs(onMessage) + M3: sendPreviewTemplate / onPreviewTemplateResult
├── stores/
│   ├── configStore.js       # useConfigStore: list / current / loading / error + actions
│   ├── pageFetchStore.js    # M2: usePageFetchStore: status / lastResult / lastError + fetch()
│   ├── browserSessionStore.js  # M2.5: useBrowserSessionStore: status / lastScreenshot / selectors / previewResult / saveFieldResult + loadUrl/click/preview/saveField
│   └── extractionPreviewStore.js  # M3: useExtractionPreviewStore: results/warnings per pageType + triggerPreview/getResult/getWarnings
├── router/
│   └── index.js             # / → /configs, /configs, /configs/new, /configs/:id, /configs/:id/preview
└── views/
    ├── ConfigList.vue       # 列表 + 新建/编辑/删除/预览按钮 + 分页
    ├── ConfigEdit.vue       # 新建/编辑双模式 + 字段动态增删 + 打开预览入口
    └── PagePreview.vue      # M2.5 + M3: el-tabs 容器，Tab1=造字段，Tab2=按模板预览
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
| `crawl-execution` | ⬜ 未开始 | 新增 `service/CrawlEngine.java`，ThreadPoolExecutor 控制并发；直接复用 `ExtractionService` 作为字段提取内核 |
| `data-persistence` | ⬜ 未开始 | 新增 `list_page` / `list_item` / `article` / `crawl_task` 实体 |

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
