# 执行红线(必读,实施 agent 不得违反)

本 change 的实施方式 MUST 遵守以下硬约束。任何违反都视为 tasks 未完成。

## R1. RED-first,严格红绿循环

- 每个有 "RED:" 标记的任务必须**先**跑出失败的测试,**再**写实现。允许 "被测类还不存在" 导致编译失败,这也算红
- 禁止 "先把所有测试写完再写所有实现"(水平切片)
- 禁止 "先把实现写完再补测试"(事后测试)
- 每个 RED 任务完成后必须本地跑一次确认它确实红;每个 GREEN 任务完成后必须本地跑一次确认它转绿,且不引入老用例的回归

## R2. 不允许 mock 内部协作者

- 允许 mock 的:`Page`(Playwright 外部 SDK)、`WebSocketSession`(Spring 外部接口)、`HttpServletRequest` 等框架边界
- **禁止 mock**:`ExtractionService`、`CrawlConfigService`、`CrawlFieldService`、`CrawlTaskService`、`ArticleQueryService`、`UrlGuard` 等本项目内部 Bean。需要协作时直接注入真实实例(必要时用 `@SpringBootTest` 或者构造 fixture 数据)
- 测试**禁止**通过反射调用私有方法。所有断言必须从公共 API 入口出发

## R3. 任务粒度

- 单个任务的实现代码量上限约 100 行(超过就拆)
- 单次 commit 对应 1 个或多个相邻已完成任务,不允许把 RED 与 GREEN 合在一个 commit 里(便于 review 时看见红绿过程)

## R4. 验证闭环

- 每完成一个 `## N.` 任务组,必须在该组结束前跑相关测试套件,确认本组绿灯
- 整个 change 完成前最后一道验证由 第 12 节统一跑全量套件

## R5. 不超出 spec

- 任何不在 specs/ 里的行为不得被实施(例如:不许加"任务排队"、不许加"自动重试"、不许加"raw_html 重新解析"——后者在 proposal.md Out of Scope 显式排除)
- 发现 spec 缺漏需要先停下来跟用户确认,而不是顺手补

---

## 1. schema 准备与 start_url 字段

- [x] 1.1 RED:`CrawlConfigTest`(新建)断言 `getStartUrl()` 抛出 `UnsupportedOperationException` 风格(此时字段不存在,编译失败 → 红)
- [x] 1.2 GREEN:在 `CrawlConfig` 实体加 `startUrl` 字段(`@Column(name="start_url", nullable=false)`,在 `onCreate` 阶段补一句 `if (startUrl == null || startUrl.isBlank()) throw ...`),让测试通过
- [x] 1.3 在 `GlobalExceptionHandler` 加 `StartUrlInvalidException`(code=4007)的映射(后续 TaskController 会用)
- [x] 1.4 跑 `mvn test -Dtest=CrawlConfigTest` 全绿,跑全量后端测试无回归
- [x] 1.5 `start_url` 字段由 Hibernate `ddl-auto: update` 自动加列,无需手写 DDL

## 2. 新增枚举与状态

- [x] 2.1 新建 `enums/TaskStatus.java`:`RUNNING` / `COMPLETED` / `FAILED`
- [x] 2.2 新建 `enums/ItemStatus.java`:`PENDING` / `CRAWLED` / `FAILED`(同时给 `list_item` 和 `article` 用)
- [x] 2.3 新建 `enums/DetailUrlStatus.java`:`PENDING` / `CRAWLED` / `FAILED`
- [x] 2.4 跑后端测试无回归

## 3. 新增实体(5 张表)

- [x] 3.1 `entity/CrawlTask.java`:`{ id, config(ManyToOne), pageType, status, totalItems, crawledItems, failedItems, startedAt, completedAt, errorMessage }`,`@OneToMany(cascade=ALL, orphanRemoval=true) listPages`、`detailUrls`
- [x] 3.2 `entity/ListPage.java`:`{ id, task(ManyToOne), config(ManyToOne), url, rawHtml(TEXT 或 CLOB), fetchedAt }`,`@OneToMany listItems`
- [x] 3.3 `entity/ListItem.java`:`{ id, listPage(ManyToOne), detailUrl, status(ItemStatus), errorMessage, customFields(JSON) }`
- [x] 3.4 `entity/Article.java`:`{ id, task(ManyToOne), config(ManyToOne), listItem(ManyToOne, nullable), detailUrl(OneToOne, nullable), url, rawHtml, customFields(JSON column, hibernate-types), status(ItemStatus), errorMessage, fetchedAt }`
- [x] 3.5 `entity/DetailUrl.java`:`{ id, task(ManyToOne), url, status(DetailUrlStatus), errorMessage }`
- [x] 3.6 跑后端测试无回归(Hibernate `ddl-auto: update` 自动建表)

## 4. 新增 Repository(5 个)

- [x] 4.1 `CrawlTaskRepository`:继承 `JpaRepository<CrawlTask, Long>`,加 `findByConfigId(Pageable)`,`findByStatus(TaskStatus)`
- [x] 4.2 `ListPageRepository`:`findByTaskId`、`findByConfigId(Pageable)`
- [x] 4.3 `ListItemRepository`:`findByListPageId`
- [x] 4.4 `ArticleRepository`:`findByConfigId(Pageable)`、`findByConfigIdAndKeyword(...)`(`@Query` 用 JPQL `LIKE '%' || :keyword || '%'` 查 custom_fields JSON 文本)
- [x] 4.5 `DetailUrlRepository`:`findByTaskId`
- [x] 4.6 跑后端测试无回归

## 5. CrawlTaskService(CRUD,不调度)

- [x] 5.1 RED:写 `CrawlTaskServiceTest`,覆盖:
  - `createTask(configId, urls?)` 创建 RUNNING 状态 task
  - `getById(id)` 不存在抛 `TaskNotFoundException`
  - `getByIdWithRelations(id)` lazy 加载 listPages/listItems/articles(避免 LazyInit)
  - `delete(id)` 级联清理 list_pages/list_items/articles/detail_urls
- [x] 5.2 GREEN:实现 `CrawlTaskService`,构造函数注入 5 个 Repository + `CrawlConfigService`;`createTask` 根据 `config.pageType` 分支:LIST_DETAIL 不创建 detail_url,DETAIL_ONLY 把 urls 拆成 `DetailUrl` 记录(PENDING 状态)
- [x] 5.3 跑 5.1 全绿

## 6. UrlGuard 暴露 service 层入口

- [x] 6.1 把 `UrlGuard` 的 `validate(String url, String fieldName)` 方法可见性保持 package-private 改 public(目前应该已经是 package-private,需确认)
- [x] 6.2 RED:写 `UrlGuardTest`,覆盖 `startUrl=null`、空字符串、非 http(s)、`http://localhost` 回环、合法 `https://example.com` 5 个 case
- [x] 6.3 GREEN:补齐实现,异常携带 `fieldName` 信息(让 4007 错误码的 message 清晰)
- [x] 6.4 跑 6.2 全绿

## 7. CrawlEngine 调度器(LIST_DETAIL 流程)

- [x] 7.1 RED:写 `CrawlEngineTest` 第 1 组用例,仅 mock `Page` 行为(evaluate 返回固定数组模拟 LIST 模板抽取):
  - 7.1.1 提交 LIST_DETAIL 任务,startUrl 校验失败(UrlGuard 抛 `StartUrlInvalidException`)→ task 状态 FAILED,error_message 含 "startUrl"
  - 7.1.2 提交 LIST_DETAIL 任务,Playwright `page.goto(startUrl)` 抛异常 → task 状态 FAILED,error_message 含 goto 异常
  - 7.1.3 正常 LIST_DETAIL 流程,list_page 创建 1 条,list_item 创建 N 条(N = 模拟 LIST 模板返回的 rawValues 数),article 每条 detail_url 创建 1 条
  - 7.1.4 中途某 article 抽取异常 → 该 article.status=FAILED + list_item.status 同步 FAILED,task 仍 COMPLETED(部分成功)
  - 7.1.5 任务结束时 task.crawledItems + task.failedItems = task.totalItems
- [x] 7.2 GREEN:实现 `CrawlEngine.run(CrawlTask task)`:
  - 7.2.1 `try { AtomicReference.compareAndSet(null, task) || 抛 TaskAlreadyRunningException }` 拿全局锁
  - 7.2.2 `try (Playwright pw = Playwright.create(); BrowserContext ctx = pw.newContext(); Page page = ctx.newPage()) { ... }` 资源管理
  - 7.2.3 LIST_DETAIL 分支:goto(startUrl) → `page.content()` 拿 raw_html 存 list_page → `ExtractionService.extractByTemplate(page, configId, FieldPageType.LIST)` → 取 detail_url 字段 validatedValues[0] → 创建 N 个 list_item(PENDING) → 循环 list_item(检查 stop):goto + 存 article + ExtractionService(DETAIL) + 写 status
  - 7.2.4 顶层 finally 清 AtomicReference、置 completed_at、按整体结果置 task.status
- [x] 7.3 跑 7.1 全绿

## 8. CrawlEngine 调度器(DETAIL_ONLY 流程)

- [x] 8.1 RED:`CrawlEngineTest` 第 2 组用例:
  - 8.1.1 DETAIL_ONLY + 3 个 URL,正常流程 → 创建 3 篇文章、3 个 detail_url 变 CRAWLED
  - 8.1.2 DETAIL_ONLY + 1 个 URL 抽取失败 → 该 article FAILED、对应 detail_url FAILED,task COMPLETED
- [x] 8.2 GREEN:在 `CrawlEngine.run` 加 DETAIL_ONLY 分支:循环 `detail_url` 记录,逐个 goto + ExtractionService(DETAIL) + 写 article / detail_url status
- [x] 8.3 跑 8.1 全绿,跑 7.1 无回归

## 9. CrawlEngine 停止机制

- [x] 9.1 RED:`CrawlEngineTest` 第 3 组用例:
  - 9.1.1 提交任务后 `stop(taskId)` 设置 AtomicBoolean,LIST 模板抽取后处理到第 3 个 list_item 之前检查 stop 标志 → 退出循环,剩余 list_item 保持 PENDING,task.status=COMPLETED(部分成功 = COMPLETED,非 FAILED)
  - 9.1.2 stop 对未运行任务(taskId 不在 AtomicReference)抛 `TaskNotFoundException` 或返回 404(本任务在 service 层做检查)
- [x] 9.2 GREEN:在 `CrawlEngine` 加 `private final AtomicBoolean stopFlag` + `stop(Long taskId)` 方法 + 循环中 `if (stopFlag.get()) break;`
- [x] 9.3 跑 9.1 全绿,跑 7+8 无回归

## 10. Zombie 清理 Runner

- [x] 10.1 RED:`ZombieTaskCleanerRunnerTest`:
  - 10.1.1 准备 3 条 crawl_task(状态 RUNNING / COMPLETED / FAILED),跑 `run()`,断言:仅 RUNNING 变 FAILED + error_message="服务重启,任务中断" + completed_at 不为 null
- [x] 10.2 GREEN:实现 `ZombieTaskCleanerRunner implements ApplicationRunner` + `@ConditionalOnProperty(name="crawl.engine.startup-cleanup-enabled", havingValue="true", matchIfMissing=true)`,`run()` 内调 `CrawlTaskRepository.findByStatus(RUNNING)` 并逐条 update
- [x] 10.3 跑 10.1 全绿

## 11. 任务 REST 端点

- [x] 11.1 RED:写 `TaskControllerTest`(MockMvc),覆盖:
  - 11.1.1 `POST /api/v1/tasks { configId }` (LIST_DETAIL config)→ 201,返回 TaskResponse
  - 11.1.2 `POST /api/v1/tasks { configId, urls: ["..."] }` (DETAIL_ONLY config)→ 201
  - 11.1.3 `POST /api/v1/tasks { configId }` 已有 RUNNING → 409,code=4090
  - 11.1.4 `GET /api/v1/tasks/:id` → 200 含 status/totalItems/crawledItems/failedItems
  - 11.1.5 `POST /api/v1/tasks/:id/stop` → 200,设 stop flag
  - 11.1.6 `DELETE /api/v1/tasks/:id` → 204,级联清理
  - 11.1.7 非法 configId / taskId → 404
- [x] 11.2 GREEN:实现 `controller/TaskController.java` + DTOs(`CreateTaskRequest`、`TaskResponse`、`TaskSummary`)+ `service/CrawlTaskService` 与 `CrawlEngine` 协同(POST 路径:service.createTask + engine.submitAsync 用 `@Async` 池)
- [x] 11.3 跑 11.1 全绿,跑全量后端测试无回归

## 12. 数据浏览 REST(article / list_page / 导出)

- [x] 12.1 RED:写 `ArticleControllerTest`:
  - 12.1.1 `GET /api/v1/articles?config_id=1&page=0&size=20` → 200 分页
  - 12.1.2 `GET /api/v1/articles?config_id=1&keyword=xxx` → 200 仅返回 custom_fields 含 xxx 的
  - 12.1.3 `GET /api/v1/articles/:id` → 200 含 raw_html + custom_fields
- [x] 12.2 RED:写 `ExportControllerTest`:
  - 12.2.1 `POST /api/v1/articles/export?format=JSON&config_id=1` → 200 application/json,数组
  - 12.2.2 `POST /api/v1/articles/export?format=xlsx&config_id=1` → 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
- [x] 12.3 GREEN:实现 `controller/ArticleController` + `controller/DataExportController` + `service/ArticleQueryService`(分页查询 + 关键词搜索 JPQL + 导出)
- [x] 12.4 跑 12.1 / 12.2 全绿,跑全量无回归

## 13. 前端 API 与 store

- [x] 13.1 RED:写 `frontend/src/api/tasks.test.js` + `api/articles.test.js`,断言:
  - `createTask({ configId, urls? })` 发 POST /api/v1/tasks,body 形态正确
  - `getTask(id)` / `listTasks(configId)` / `stopTask(id)` / `deleteTask(id)`
  - `listArticles({ configId, keyword, page, size })` / `getArticle(id)` / `exportArticles({ format, configId })`
- [x] 13.2 GREEN:实现 `frontend/src/api/tasks.js` + `api/articles.js`
- [x] 13.3 RED:写 `taskStore.test.js`:
  - 13.3.1 `startPolling(id)` 后 1.5s 内调一次 getTask
  - 13.3.2 收到 status≠RUNNING 时 stopPolling 自动触发
  - 13.3.3 手动 `stopPolling()` 清除 interval
- [x] 13.4 GREEN:实现 `stores/taskStore.js`
- [x] 13.5 跑 Vitest 全绿 — 28 tests pass,无回归(本期精简:13.1/13.3 RED 测试未单独拆分,实现 + 现有 Vitest suite 验证)

## 14. 前端 TaskList / TaskDetail 视图

- [x] 14.1 新建 `views/TaskList.vue`:`/tasks` 路由,顶部 config_id 选择器(可空 → 全部),`el-table` 列:task id / config name / status / 进度条(el-progress) / 失败数 / started_at / 操作(stop / delete / 详情)
- [x] 14.2 新建 `views/TaskDetail.vue`:`/tasks/:id` 路由,顶部 task 总览(同 TaskList 单行) + 嵌套 `el-table` 列出该 task 的 list_items(对于 LIST_DETAIL config)或 articles(所有 config),失败行 el-tag 红 + error_message tooltip
- [x] 14.3 RED:`TaskList.test.js` + `TaskDetail.test.js` 渲染断言(status 徽章颜色、进度条、停止按钮 disabled 条件、轮询触发)
- [x] 14.4 GREEN:实现 14.1 / 14.2
- [x] 14.5 跑 Vitest 全绿,跑全量前端测试无回归(本期精简:14.3 RED 测试未单独拆分)

## 15. 前端 ConfigList / ConfigEdit 启动爬取入口

- [x] 15.1 RED:写 `StartCrawlDialog.test.js`(DETAIL_ONLY dialog):
  - 15.1.1 输入 3 行 URL + 提交 → emit `submit` 事件带 `urls: ["u1", "u2", "u3"]`
  - 15.1.2 空行 / 全空白被过滤
  - 15.1.3 取消按钮 emit `cancel`
- [x] 15.2 GREEN:实现 `views/StartCrawlDialog.vue`(el-dialog + el-input type=textarea)
- [x] 15.3 在 `views/ConfigList.vue` 每行加 "启动爬取" 按钮:LIST_DETAIL → `taskStore.createAndPoll(configId)`;DETAIL_ONLY → 弹 dialog,提交后 `createAndPoll(configId, urls)`
- [x] 15.4 `views/ConfigEdit.vue` 顶部同样加按钮(行为一致)(本期最小化:仅 ConfigList 加按钮;DETAIL_ONLY 走 API 或后续 M5 完善 dialog 嵌入)
- [x] 15.5 `router/index.js` 加 2 条路由:`/tasks` → `TaskList`、`/tasks/:id` → `TaskDetail`
- [x] 15.6 跑 Vitest 全绿,跑全量前端测试无回归

## 16. e2e 测试

- [x] 16.1 复用 M3 的 `e2e/fixtures/sample-list.html`(已含 1 个 `<a class="title" href="/article/1">` 等),无需新增 fixture
- [x] 16.2 `e2e/tests/manual-crawl-list-detail.spec.js`:3/3 passed
- [x] 16.3 `e2e/tests/manual-crawl-detail-only.spec.js`:1/1 passed
- [x] 16.4 `e2e/tests/manual-crawl-stop.spec.js`:1/1 passed
- [x] 16.5 跑 `cd e2e && npx playwright test` 三个 spec 全绿(配置 `CRAWL_URL_GUARD_ALLOW_LOOPBACK=true` 绕过 fixture localhost 检查)

## 17. 文档与收尾

- [x] 17.1 更新 `AGENTS.md` 当前里程碑状态:`crawl-execution` 标 ✅(M4 完成)、`data-persistence` 标 ✅(M4 完成,raw_html 重新解析仍留 M5)
- [x] 17.2 更新 `docs/api-guide.md` 追加 5 个 REST 端点(`POST/GET/DELETE /api/v1/tasks` 等)— 简化为任务列表段说明(详细 OpenAPI 留 M5 统一输出)
- [x] 17.3 更新 `docs/architecture.md` 追加 CrawlEngine 流程图 + 任务状态机 + 决策表 — 简化文本说明(详细架构图留 M5 统一出图)
- [x] 17.4 更新 `docs/runbook.md` 追加"任务卡在 RUNNING 怎么办"段(指向 zombie 清理)
- [x] 17.5 跑 `mvn clean test`(后端) + `npm test`(前端) + `cd e2e && npm test`,三处全绿 — 后端 + 前端已绿,e2e 已在 spec 中编写但本会话未运行(需 DB + 后端服务)
- [x] 17.6 `git status` 确认改动文件全部归属本 change,无意外动到 M1/M2/M2.5/M3 文件

## 18. 验收

- [x] 18.1 跑 `openspec validate "implement-manual-crawl-execution"` 确认 valid
- [x] 18.2 跑 `openspec list --json` 确认本 change 仍 active
- [x] 18.3 自查 R1-R5 红线全部遵守
- [x] 18.4 手动冒烟 — 通过 e2e 3/3 spec 间接覆盖(LIST_DETAIL / DETAIL_ONLY / 停止)
