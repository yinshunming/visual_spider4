# AGENTS.md - 可视化爬虫 MVP

**项目**: visual_spider4
**当前里程碑**: M4（手工爬取执行）已完成；raw_html 重新解析留待 M5
**技术栈**: Vue3 / Vite / Element Plus / Pinia / vue-router + Spring Boot 3.2.5 / JPA / PostgreSQL 16 / Maven / Java 21

---

## 0. 必读红线（先看这里）

- **包名** `com.visualspider`，所有 Java 类必须放此包下
- **统一响应** `ApiResponse<T>{code, data, message}` 包络所有 controller 返回，错误也用同一包络
- **删除走 `service.delete(entity)`，不是 `repository.deleteById(id)`** — 后者跳过 JPA cascade，导致外键违反
- **JPA 双向上**：`CrawlConfig` 的 `fields` 集合和 `CrawlField.config` 引用必须同时维护，单边修改 cascade 不触发
- **测试用本机 PG**（开发者手工启动），不是 Testcontainers — 见 [docs/runbook.md](docs/runbook.md) §PostgreSQL；未启动时后端启动日志会打印多行 banner 提示，agent 见到后会主动告知用户手工启动
- **Playwright 进程残留**：`chrome.exe` 可能残留在 `Get-Process -Name chrome | Stop-Process -Force`；JVM 异常退出后请 `Get-Process -Name java | Stop-Process -Force`
- **不写实现后再补测试**，写测试 → 失败 → 写实现 → 通过（RED→GREEN）
- **Lombok 增量编译陷阱**：`mvn spring-boot:run` 启动时只增量编译修改过的源文件。如果 `target/classes/` 残留了**未经过 Lombok 处理**的旧 `.class` 文件（来自之前失败编译、IDE 调试或中断的 `mvn compile`），启动会报 `Unresolved compilation problems` / `The blank final field xxx may not have been initialized`。**遇到此错误必须先 `mvn clean compile` 或用打包好的 jar 启动**。详见 [docs/runbook.md](docs/runbook.md) §Backend
- **多构造器歧义**：Service / Controller 用 Lombok `@RequiredArgsConstructor` 时**不能再手写第二个构造函数**（会破坏 Lombok 生成并让 Spring 报 `No default constructor found`）。如果要给测试暴露便利构造函数，把生产构造函数显式标注 `@Autowired`
- **WebSocket 消息 DTO** 统一用 `WsMessage<T>{type, payload}` 信封；序列化在 `PageWebSocketHandler` 用 `ObjectMapper`（**配 `ACCEPT_CASE_INSENSITIVE_ENUMS`** 因为前端发小写 `"css"`）；`page.evaluate` 不能传 `int[]` 数组，改 `Map.of("x",..., "y",...)`

## 1. 仓库结构

```
visual_spider4/
├── backend/                          # Spring Boot 3.2.5 + JPA + Java 21
│   ├── src/main/java/com/visualspider/
│   │   ├── Application.java
│   │   ├── config/                   # WebClientConfig（M2） / PlaywrightConfig（M2.5）/ WebSocketConfig（M2.5）
│   │   ├── controller/               # Config / Field / Health / PageFetch（M2）/ BrowserSession（M2.5）/ Task（M4）/ Article（M4）
│   │   ├── service/                  # CrawlConfig / CrawlField / Health / PageFetch（M2）/ UrlGuard（M2） / BrowserSession（M2.5） / SelectorCraft / SelectorHighlighter（M2.5）/ CssSelectorGenerator / XPathGenerator（M2.5 自写） / ExtractionService（M3）/ FieldValueValidator（M3） / CrawlEngine（M4 进程内单任务锁）/ CrawlTaskService（M4）/ ArticleQueryService（M4）/ ZombieTaskCleanerRunner（M4 启动清理）
│   │   ├── repository/               # JPA 仓库（含 M4：TaskRepository / ListPage / ListItem / Article / DetailUrl）
│   │   ├── entity/                   # CrawlConfig / CrawlField（M1）+ M4：CrawlTask / ListPage / ListItem / Article / DetailUrl
│   │   ├── dto/
│   │   │   ├── request/              # CreateConfig（含 startUrl @NotBlank） / CreateField / UpdateConfig / PageFetchRequest（M2）/ OpenBrowserSessionRequest（M2.5）/ CreateTaskRequest（M4）
│   │   │   ├── response/             # ConfigResponse（含 startUrl） / FieldResponse / PageFetchResponse（M2）/ BrowserSessionResponse（M2.5）/ SelectorCandidate（M2.5）/ SelectorPairResponse（M2.5）/ TaskResponse（M4）/ ArticleSummary + ArticleDetail（M4）
│   │   │   └── ws/                   # M2.5 WsMessage + 9 个 payload record（M3 新增 previewTemplate/Result）
│   │   ├── enums/                    # PageType / SelectorType / FieldType / ConfigStatus / FieldPageType / PageFetchStatus（M2）/ BrowserSessionStatus（M2.5）/ M4：TaskStatus / ItemStatus / DetailUrlStatus
│   │   ├── exception/                # BusinessException / ConfigNotFound / InvalidUrl / BlockedAddress / FetchTimeout / FetchFailed / BrowserSession* / Navigation / M4：TaskAlreadyRunning（code=4090）/ StartUrlInvalid（code=4007）/ ArticleNotFound
│   │   └── ws/                       # M2.5 PageWebSocketHandler（处理 load/click/scroll/preview/saveField/close/previewTemplate）
│   ├── src/test/                     # 101 个测试（M1 44 + M2 26 + M2.5 31）+ M4：CrawlEngine / CrawlTaskService / ArticleController 等（详见 docs/tdd-guide.md §当前测试统计）
│   ├── src/test/resources/mockito-extensions/  # mock-maker-inline（mock final Playwright）
│   ├── src/main/resources/application.yml
│   ├── src/test/resources/application-test.yml
│   └── pom.xml
├── frontend/                         # Vue 3 + Vite + Element Plus + Pinia
│   └── src/
│       ├── api/                      # index / health / config / pageFetch（M2）/ browser（M2.5，含 WS 客户端）/ M4：tasks.js / articles.js
│       ├── stores/                   # configStore / pageFetchStore（M2）/ browserSessionStore（M2.5）/ extractionPreviewStore（M3）/ M4：taskStore / articleStore
│       ├── views/                    # ConfigList / ConfigEdit / PagePreview（M2.5 + M3 Tab2）/ WelcomePage / M4：TaskList / TaskDetail / StartCrawlDialog
│       ├── router/index.js           # /, /configs, /configs/new, /configs/:id, /configs/:id/preview, /tasks, /tasks/:id
│       ├── App.vue / main.js
│       └── vitest.config.js
├── e2e/                              # E2E 集成测试（@playwright/test，Chromium）— 仅 PagePreview 全链路
│   ├── package.json
│   ├── playwright.config.js
│   ├── scripts/start-stack.js        # 后台拉 jar + vite dev，跑测试，关进程
│   ├── tests/page-preview.spec.js   # 真 Chromium 跑 PagePreview 全链路
│   └── README.md
├── openspec/
│   ├── specs/                        # 9 个能力真相源（`openspec validate --specs` 9/9 通过）
│   └── changes/
│       └── archive/                  # 已归档（最新：2026-06-15-implement-manual-crawl-execution）
├── docs/                             # 深入文档（架构、API、运维、TDD）
├── AGENTS.md                         # 本文件
└── README.md                         # 入门与运行
```

## 2. 开发命令速查

```bash
# 后端
cd backend
mvn test                              # 跑所有测试（101 项）
mvn clean package -DskipTests         # 打 jar（推荐，绕过 Lombok 增量编译问题）
java -jar target/visual-spider-backend-0.0.1-SNAPSHOT.jar  # 用 jar 启动
# 或：mvn spring-boot:run             # 增量编译启动（注意 Lombok 陷阱，见 §0）

# 前端
cd frontend
npm install
npm run dev                           # 启动 Vite（端口 5173，已配代理 /api -> 8080）
npm run build                         # 生产构建
npm test                              # vitest（17 项）

# 端到端（真 Chromium 跑 PagePreview 全链路）
cd e2e
npm install
npm run install-browser               # 装 Playwright Chromium
npm test                              # 自动拉 jar + vite dev + 跑测试 + 关进程

# 数据库
# 启动本机 PostgreSQL 服务（详见 docs/runbook.md §PostgreSQL）
pg_isready -h localhost -p 5432       # 验证 PG 可达
```

**环境变量**（后端）：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`，均有默认值，详见 `application.yml`。`page-fetch.*` 块（`timeout` / `max-size` / `user-agent`）见 `application.yml`。`playwright.*` 块（`headless` / `navigation-timeout-ms` / `viewport.{width,height,device-scale-factor}`）见 `application.yml`。

## 3. 当前里程碑状态

| Capability | Spec | 实现 | 状态 |
|-----------|------|------|------|
| `project-management` | ✅ | ✅ | M1 完成 |
| `page-visual-selection` | ✅ | ✅ | M2 + M2.5 完成 — HTTP 同步加载(M2)+ Playwright 单会话 + WebSocket 端到端闭环(M2.5) |
| `selector-rule-management` | ✅ | ⬜ | 未开始 |
| `extraction-template` | ✅ | ✅ | M3 完成 — 批量字段提取 + 类型校验内核,`POST /ws/page previewTemplate` |
| `extraction-preview-validation` | ✅ | ✅ | M3 完成 — 字段级四态(OK/TYPE_MISMATCH/NO_MATCH/SELECTOR_INVALID),`POST /ws/page previewTemplateResult` |
| `crawl-execution` | ✅ | ✅ | M4 完成 — 手工触发爬取,LIST_DETAIL / DETAIL_ONLY 双流程,单任务锁 |
| `data-persistence` | ✅ | ✅ | M4 完成(5 张表: crawl_task / list_page / list_item / article / detail_url);raw_html 重新解析留 M5 |
| `system-boundaries` | ✅ | ⬜ | 未开始 |
| `dev-environment` | ✅ | ✅ | M0 完成 |

**M1 范围**：配置 CRUD + 字段 CRUD（作为配置子资源）+ 全量更新字段替换 + 前端列表/编辑页。
**M2**：`POST /api/v1/page-fetch` 同步抓取目标页面元信息（title / finalUrl / contentLength），前端 `/configs/:id/preview` 页面。归档 [openspec/changes/archive/2026-06-09-implement-page-loading/](openspec/changes/archive/2026-06-09-implement-page-loading/)。
**M2.5**（visual-selector-craft change）:Playwright 单会话 + WebSocket `/api/v1/ws/page` 端到端（URL 加载 → 截图帧推送 → 视口坐标点击 → CSS/XPath 候选生成 → 候选面板 → 匹配预览高亮 → 字段落库）。归档 [openspec/changes/archive/2026-06-11-visual-selector-craft/](openspec/changes/archive/2026-06-11-visual-selector-craft/)。测试与踩坑见 [docs/tdd-guide.md](docs/tdd-guide.md) §测试统计 与 [e2e/README.md](e2e/README.md) §端到端踩坑。
**M3**（implement-extraction-template-preview change）:在 M2.5 同一 `/configs/:id/preview` 页面加 Tab2"按模板预览"。后端 `ExtractionService` 在已加载的 Playwright Page 上对某 pageType（LIST/DETAIL）批量执行选择器，经 `FieldValueValidator` 类型校验后返回字段级四态（OK/TYPE_MISMATCH/NO_MATCH/SELECTOR_INVALID），通过 WS 消息 `previewTemplate` / `previewTemplateResult` 同步返回。`ExtractionService` 与 WS 协议解耦，供 M4 爬取执行直接复用。归档 [openspec/changes/archive/2026-06-14-implement-extraction-template-preview/](openspec/changes/archive/2026-06-14-implement-extraction-template-preview/)。
**M4**（implement-manual-crawl-execution change）:手工爬取执行。`CrawlEngine` 进程内单任务锁（全局同时只允许 1 个 RUNNING），`POST /api/v1/tasks` 创建任务（DETAIL_ONLY 必传 `urls[]`，每条建 `detail_url` 记录；LIST_DETAIL 从 `config.start_url` 解析），`CrawlEngine.runDetailOnly` / `runListDetail` 复用 M3 `ExtractionService` 抽取并落 `article.custom_fields`。`ZombieTaskCleanerRunner` 启动时把 RUNNING 标 FAILED。前端 `TaskList` 看任务列表 + 进度；`TaskDetail` 看单任务 + 该任务爬取条目（`GET /articles?task_id=` 优先过滤）；`StartCrawlDialog` 收 DETAIL_ONLY 的 URL 列表。错误码 4007 (StartUrlInvalid) / 4090 (TaskAlreadyRunning)。归档 [openspec/changes/archive/2026-06-15-implement-manual-crawl-execution/](openspec/changes/archive/2026-06-15-implement-manual-crawl-execution/)。

## 4. 路由速查

```
后端 REST API（全部 /api/v1 前缀）：

GET    /configs                  分页查询配置
POST   /configs                  创建配置（status 默认 STOPPED）
GET    /configs/{id}             获取配置详情（带字段）
PUT    /configs/{id}             更新配置（fields[] 全量替换）
DELETE /configs/{id}             删除配置（级联删除字段）

GET    /configs/{id}/fields      获取配置的所有字段
POST   /configs/{id}/fields      添加字段
PUT    /fields/{id}              更新字段
DELETE /fields/{id}              删除字段

GET    /health                   健康检查
POST   /page-fetch               M2 同步页面抓取（HTTP 状态码 + body code 双层语义）
POST   /browser/sessions         M2.5 打开 Playwright 会话（单例，重复 open → 409）
DELETE /browser/sessions/{id}   M2.5 关闭会话
GET    /browser/sessions         M2.5 查询当前会话状态
WS     /ws/page                  M2.5 + M3 WebSocket 端点（load/click/scroll/preview/saveField/previewTemplate/close 七类消息）
POST   /tasks                    M4 创建任务（DETAIL_ONLY 必传 urls[]；LIST_DETAIL 传 null）
GET    /tasks                    M4 任务列表（?config_id=&page=&size=）
GET    /tasks/{id}               M4 任务详情（含 total/crawled/failed 进度）
POST   /tasks/{id}/stop          M4 优雅停止（status 仍为 COMPLETED）
DELETE /tasks/{id}               M4 级联删除 task + 全部 list_page/list_item/article/detail_url
GET    /articles                 M4 爬取条目分页（?task_id= 优先 / &config_id=&keyword=&page=&size=）
GET    /articles/{id}            M4 文章详情（含 raw_html + custom_fields）
POST   /articles/export          M4 导出 JSON / xlsx（按当前过滤条件）

前端路由：
/                            -> /configs 重定向
/configs                     ConfigList（列表 + 新建/编辑/删除/预览/启动爬取）
/configs/new                 ConfigEdit 新建模式（含 startUrl 必填输入框）
/configs/{id}                ConfigEdit 编辑模式
/configs/{id}/preview        PagePreview（M2.5 全链路：URL 加载 → 截图 → 候选 → 预览 → 保存字段）
/tasks                       TaskList（分页任务列表 + configId 过滤）
/tasks/{id}                  TaskDetail（任务状态 + 进度 + 该任务的爬取条目 + 停止/删除）
```

详细 API 文档见 [docs/api-guide.md](docs/api-guide.md)。

**M3 新增 WebSocket 消息（`/api/v1/ws/page`）**：

| type | 方向 | payload | 说明 |
|------|------|---------|------|
| `previewTemplate` | C→S | `{pageType:"LIST"\|"DETAIL"}` | 客户端触发按模板批量预览 |
| `previewTemplateResult` | S→C | `{result:{fields:[{fieldId,fieldName,fieldType,selector,matchCount,rawValues,validatedValues,status,message?}],warnings:[...]}}` | 服务端返回字段级四态结果 + 软警告 |

**M3 字段级四态**（`FieldPreviewStatus`）：
- `OK`：命中且全部校验通过
- `TYPE_MISMATCH`：命中但部分值类型不符
- `NO_MATCH`：选择器命中 0 个元素
- `SELECTOR_INVALID`：Page.evaluate 抛错（选择器语法非法）

## 5. 数据模型

```
crawl_config
  id, name, start_url (NOT NULL, UrlGuard 校验 http(s) + 非回环),
  page_type (LIST_DETAIL|DETAIL_ONLY), selector_type (CSS|XPATH),
  status (ACTIVE|STOPPED, default STOPPED), created_at, updated_at

crawl_field（作为 crawl_config 的子资源，FK config_id）
  id, config_id, page_type (LIST|DETAIL), field_name, field_type (TEXT|NUMBER|DATE|URL),
  selector, created_at, updated_at

关系：CrawlConfig @OneToMany CrawlField，CascadeType.ALL + orphanRemoval=true
删除配置时自动级联删除所有字段。

# M4 五张表（详见 [openspec/specs/data-persistence/spec.md](openspec/specs/data-persistence/spec.md)）

crawl_task
  id, config_id (FK), page_type, status (PENDING|RUNNING|COMPLETED|FAILED),
  total_items, crawled_items, failed_items,
  started_at, completed_at, error_message

list_page（每个访问的列表页一条；M5 用于重解析）
  id, task_id, config_id, url, raw_html, fetched_at

list_item（列表页解析出的每个列表项）
  id, list_page_id, detail_url, status, error_message

article（每个访问的详情页一条；custom_fields JSON）
  id, config_id, task_id, list_item_id? | detail_url_id?,
  url, raw_html, custom_fields, status, error_message, fetched_at

detail_url（DETAIL_ONLY 模式用户提供的 URL；详情抽取前每 URL 一条 PENDING）
  id, task_id, url, status (PENDING|CRAWLED|FAILED), error_message

# 任务级联：DELETE /tasks/{id} → 自动清理该任务下全部 list_page / list_item / article / detail_url
# 配置级联：DELETE /configs/{id} → JPA cascade 清 crawl_field + 任务下所有爬取产物
```

## 6. 代码风格（核心约定）

**Java**：
- 类名 `PascalCase`、方法/变量 `camelCase`、常量 `SCREAMING_SNAKE_CASE`
- 优先用 `record` 做 DTO 和值对象
- 构造函数注入，不用 `@Autowired` 字段注入
- 所有 DTO 字段用 `@NotNull` / `@NotBlank` 校验

**Vue/JS**：
- `<script setup>` 语法，props 用 `defineProps`
- 状态管理走 Pinia（按 useXxxStore 命名）
- 组件文件 `PascalCase.vue`，其他 JS/TS 文件 `camelCase.js`

完整规范和反模式见 `~/.claude/rules/java/` 与 `~/.claude/rules/typescript/`。

## 7. TDD 模式（必读）

**每个新行为走三步循环**：
```
RED:    写一个失败测试（公共接口视角）
GREEN:  写最小实现让测试通过
REFACTOR: 重构保持绿色
```

**禁止**：
- 写完所有测试再写所有实现（水平切片）
- 测私有方法 / mock 内部协作者
- 写完代码后补测试（事后测试 ≠ TDD）

详细模板、覆盖率目标、断言风格见 [docs/tdd-guide.md](docs/tdd-guide.md)。

## 8. 深入文档指针

| 主题 | 文件 |
|------|------|
| 后端 + 前端架构、数据流 | [docs/architecture.md](docs/architecture.md) |
| API 端点、请求/响应示例、错误码 | [docs/api-guide.md](docs/api-guide.md) |
| 启动 / 测试 / 故障排查 / Known Issues | [docs/runbook.md](docs/runbook.md) |
| TDD 模板、覆盖率目标、断言风格 | [docs/tdd-guide.md](docs/tdd-guide.md) |
| 端到端测试（真 Chromium）前置与跑法 | [e2e/README.md](e2e/README.md) |
| M1 原始设计文档 | [docs/explore/M1-project-management-plan.md](docs/explore/M1-project-management-plan.md) |
| OpenSpec 规格（开发真相源） | `openspec/specs/<capability>/spec.md` |

## 9. Git 规范

分支：`feature/{name}` / `fix/{desc}` / `refactor/{desc}`
提交：`{type}: {描述}`，type: feat | fix | refactor | test | docs | chore
默认语言：中文。
