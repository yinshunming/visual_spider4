## Why

M3 把"按模板批量预览"做完了,用户已经能在 Playwright 页面上看到一组字段能否正确抽取。但 spec 里 `crawl-execution` 与 `data-persistence` 描述的真实爬取闭环尚未落地:**用户没法把自己配置好的项目跑一遍,没法看到爬取任务的状态与失败明细,也没法浏览爬回来的数据**。换句话说,产品至今无法完成"配置 → 跑 → 看结果"的最短主线。

本期把这两条 spec 从"应当存在"推进到"实际可工作":
- 一次手工触发的爬取任务,按 LIST_DETAIL / DETAIL_ONLY 两种模式走完整流程
- 任务进度、失败原因、爬取结果以 REST 形式对前端可读
- 任何用户都能在一个页面里看到"我提交了哪些任务、跑到哪了、哪些 URL 失败了、爬回来的字段长什么样"

## What Changes

- `crawl_config` 表新增 `start_url` 字段(必填,经 UrlGuard 校验)
- 新增 5 张表:`crawl_task` / `list_page` / `list_item` / `article` / `detail_url`,承载爬取产物与任务状态
- 新增 `CrawlEngine` 调度器:任务自开 Playwright `BrowserContext`,与 M2.5 单例 BrowserSession 互不干扰
- 新增任务生命周期 REST:`POST /api/v1/tasks`(创建并启动)、`GET /api/v1/tasks/:id`(进度轮询)、`POST /api/v1/tasks/:id/stop`(优雅停止)、`DELETE /api/v1/tasks/:id`(级联清理)
- 新增数据浏览 REST:`GET /api/v1/articles?...`(分页 + 关键词搜索)、`GET /api/v1/articles/:id`(详情含 raw_html 与 custom_fields)、`GET /api/v1/list-pages?...`、`GET /api/v1/list-items?list_page_id=...`、`POST /api/v1/articles/export?format=JSON|xlsx`
- 全局同时只允许 1 个 RUNNING 任务(`AtomicReference<RunningTask>` 进程内锁),新任务遇锁返回 409
- 服务启动时 `@PostConstruct` runner 将 zombie RUNNING 任务标 FAILED,避免 UI 看到永不结束的任务
- 字段提取复用 M3 `ExtractionService.extractByTemplate(Page, configId, pageType)`,不重写抽取内核
- 复用 M2 `UrlGuard` 校验 `start_url` 与 `detail_url` 协议白名单
- 前端新增 `/tasks`(任务列表)与 `/tasks/:id`(任务详情含进度条、失败原因、嵌套 article 列表)两个路由
- 前端 `ConfigList` / `ConfigEdit` 增加"启动爬取"按钮:DETAIL_ONLY 弹多行 textarea 收集 URL;LIST_DETAIL 直接 POST 任务

## Capabilities

### New Capabilities

无。新行为全部归属已存在的 `crawl-execution` 与 `data-persistence` 两条 spec。

### Modified Capabilities

- `crawl-execution`:在已有 Requirement 基础上,显式声明
  - `start_url` 在 `CrawlConfig` 上是必填字段,创建时由 `UrlGuard` 校验
  - 全局同时只允许 1 个任务处于 RUNNING,新任务遇锁返回 409
  - 服务启动时 zombie RUNNING 任务统一标 FAILED,error_message 填"服务重启,任务中断"
  - LIST_DETAIL 模式下,`article` 通过 `list_item_id` 关联回 `list_item`;DETAIL_ONLY 模式下,通过 `detail_url_id` 关联回 `detail_url` 记录
  - article 抽取失败时同步把 list_item.status 标为 FAILED
- `data-persistence`:在已有 Requirement 基础上,显式声明
  - `crawl_task` 顶层 `error_message` 仅在状态 FAILED 或被外部停止时填,正常运行结束不填
  - `article.error_message` 与 `list_item.error_message` / `detail_url.error_message` 记录该项的浏览器导航异常 / JS 异常 / 抽取异常
  - 删除任务时级联清理其下 `list_page` → `list_item` → `article` 与 `detail_url` → `article` 整条链
  - 导出 API(JSON / xlsx)的列集合 = 当前过滤结果中所有 article 的 `custom_fields` 键的并集

## Impact

- **后端代码**:新增 5 个实体(`CrawlTask` / `ListPage` / `ListItem` / `Article` / `DetailUrl`)+ 4 个状态枚举(`TaskStatus` / `ItemStatus` 复用给 list_item 和 article / `DetailUrlStatus`)+ 5 个 JPA Repository;新增 `service/CrawlEngine`(调度器)、`service/CrawlTaskService`(CRUD)、`service/ArticleQueryService`(浏览+导出)、`service/ZombieTaskCleanerRunner`(`@PostConstruct`);新增 `controller/TaskController`(`/api/v1/tasks`)、`controller/ArticleController`(`/api/v1/articles`)、`controller/ListPageController`(`/api/v1/list-pages`)、`controller/DataExportController`(`/api/v1/articles/export`);新增 DTO 若干(`CreateTaskRequest` / `TaskResponse` / `ArticleSummary` / `ArticleDetail` / `ListPageSummary` / `ListItemSummary`);新增 4 个领域异常(`TaskNotFoundException` / `TaskAlreadyRunningException` / `StartUrlInvalidException` / `CrawlExecutionException`)。`CrawlConfig` 实体加 `startUrl` 字段。`UrlGuard` 暴露 `validate(String url, String fieldName)` 给 controller 层使用
- **后端依赖**:`com.microsoft.playwright:playwright` 已在 M2.5 引入,`org.apache.poi:poi-ooxml` 用于 xlsx 导出(新增,需在 pom.xml 加一行 + 评估 license)
- **后端配置**:`application.yml` 新增 `crawl.engine.worker-pool-size=1`(写死单线程,暂不暴露调参);`crawl.engine.startup-cleanup-enabled=true`(默认开,可关)
- **后端测试**:新增 `CrawlTaskServiceTest`(CRUD + 级联)、`CrawlEngineTest`(单任务完整 LIST_DETAIL 流程用 mock Page 走通 + 异常隔离 + 停止信号 + zombie 清理)、`TaskControllerTest`(REST 边界)、`ArticleQueryServiceTest`(分页 + 关键词搜索 + 导出);`ExtractionServiceTest` 不动(直接复用)
- **前端代码**:新增 `api/tasks.js`(5 个方法)、`api/articles.js`(3 个方法)、`stores/taskStore.js`(状态 + 轮询)、`stores/articleStore.js`;新增 `views/TaskList.vue` / `views/TaskDetail.vue`;`views/ConfigList.vue` / `views/ConfigEdit.vue` 加"启动爬取"按钮 + `views/StartCrawlDialog.vue`(DETAIL_ONLY URL 收集);`router/index.js` 加 2 条新路由
- **前端测试**:新增 `taskStore.test.js`(轮询生命周期、状态机)、`articleStore.test.js`;`TaskList.vue` / `TaskDetail.vue` / `StartCrawlDialog.vue` 单测覆盖关键 UI
- **e2e**:新增 3 个 spec,`tests/manual-crawl-list-detail.spec.js`(LIST_DETAIL 主链路)、`tests/manual-crawl-detail-only.spec.js`(DETAIL_ONLY + URL 输入)、`tests/manual-crawl-stop.spec.js`(中途停止任务)。复用 M3 的 `e2e/fixtures/sample-list.html` 与本地静态文件服务
- **数据库**:`crawl_config` 加 1 个 NOT NULL 字段 + 5 张新表。所有 DDL 由 Hibernate `ddl-auto: update` 自动管理(M1 起就用的策略);不回填历史 config(无现网,无历史数据迁移问题)
- **文档**:`AGENTS.md` 当前里程碑状态更新;`docs/api-guide.md` 追加 5 个新 REST 端点;`docs/architecture.md` 追加 CrawlEngine 流程图 + 任务状态机;`docs/runbook.md` 追加"任务卡在 RUNNING 怎么办"段(指向 zombie 清理)
- **风险**:Playwright 上下文长期占用与服务重启时任务中断的可见性 — 由 zombie 清理 runner 兜底,UI 上能看到 `error_message="服务重启,任务中断"`;单任务运行约束可能让用户在长任务期间无法启动其他任务,UI 上以任务状态为"运行中"作明确提示,本期不提供排队

## Out of Scope(本 change 显式不做)

- 定时爬取(无 cron / schedule)
- 分布式爬取 / 分布式任务队列(无 Redis / RabbitMQ / 多节点协调)
- 复杂重试策略(单 article 失败即 FAILED,不重试;任务整体失败不重试)
- raw_html 重新解析(`extraction-template` spec 中的"从 raw_html 重新解析" Requirement 留待 M5)
- 文章浏览/导出 UI(API 全部提供,UI 仅放最小可读视图;导出按钮可手动调 API,后续 M5 补完整 UI)
- 任务排队(单任务运行约束下,新任务遇锁即 409,不支持排队等)
- 任务优先级 / 抢占 / 暂停恢复
- iframe / shadow DOM 内字段抽取(与 M2.5 / M3 保持一致)
- 历史任务归档 / 清理(数据量小,先不引入 TTL)
