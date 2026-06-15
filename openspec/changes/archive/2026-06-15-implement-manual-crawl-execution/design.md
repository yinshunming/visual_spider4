## Context

M1-M3 已经把"配置 + 字段 CRUD → 同步页面元信息抓取 → Playwright 单会话单字段造选择器 → 按模板批量预览"打通。M3 在结尾的 `ExtractionService` 注释里明确写:**"M4 爬取执行直接复用,无需重写"**。本期就是把它接上调度层与持久化层,让 spec 描述的真实爬取闭环跑起来。

技术约束(从现状与既有 spec 推导):

- M2.5 引入的 `BrowserSessionService` 是**进程内单例 Playwright session**(单个 Page),用于"点元素造选择器"交互,**不能**被爬取任务直接复用——任务期间用户还要继续用预览
- M3 的 `ExtractionService.extractByTemplate(Page page, Long configId, FieldPageType pageType)` 已经是与 WS 协议解耦的纯服务,**M4 必须直接调用,不能重写**
- M2 的 `UrlGuard` 已有协议白名单(http/https)与回环目标拦截;M4 的 `start_url` 与 `detail_url` 都必须经它校验
- `crawl_config` 表当前没有 `start_url` 字段,需要 schema 变更;无现网、无历史数据,变更成本低
- `ApiResponse` 统一包络 `{code, data, message}`,HTTP 状态码统一 200,业务错误用 `code` 区分(参考 M1 已有 `ConfigNotFoundException` 模式)
- 后端测试不接 Testcontainers(项目惯例),Playwright 走 mockito-extensions/mock-maker-inline mock
- 前端测试栈 Vitest + Vue Test Utils(已用于 M2.5/M3)
- e2e 用 Playwright(JS),已有 fixture 静态服务与 `start-stack.js` 拉起栈的脚手架

利益方:

- **用户**:能完成"配项目 → 跑一遍 → 看任务进度 → 看失败原因 → 看爬回来的数据"最短主线
- **后端开发者**:拿到 1 个可工作的端到端爬取内核,M5 的"重新解析"、M6 的"任务调度"可以在此基础上加层
- **前端开发者**:在 `ConfigList` 增加"启动爬取"入口,新增任务页两个,不破坏 M2/M2.5/M3 已有任何视图

## Goals / Non-Goals

**Goals:**

- 完成 `crawl-execution` spec 全部 4 个 Requirement 与 7 个 Scenario
- 完成 `data-persistence` spec 全部 7 个 Requirement(其中"raw_html 重新解析"暂未实现,见 Non-Goals)
- 提供任务/文章/list_page 的查询、详情、导出 REST
- 单任务运行约束与服务启动 zombie 清理
- 直接复用 M3 `ExtractionService` 抽取内核,不重写
- 前端任务列表 + 任务详情两个新视图,加 ConfigList 启动入口
- e2e 3 个 spec 覆盖主链路 + 停止

**Non-Goals:**

- 定时爬取
- 分布式 / 多节点 / 任务队列
- 复杂重试
- raw_html 重新解析(M4 不实现,留给 M5)
- 文章浏览/导出 UI 的精装修(API 全提供,UI 给最小可读视图)
- 任务排队 / 优先级 / 暂停恢复
- iframe / shadow DOM 内字段
- 历史任务 TTL 清理

## Decisions

### Decision 1: 起始 URL 放 `crawl_config.start_url`(用户拍板)

**选择**:`crawl_config` 表加 `start_url VARCHAR NOT NULL`;`POST /api/v1/configs` 必填,经 `UrlGuard.validate(startUrl, "startUrl")` 校验;`PUT /api/v1/configs/:id` 也允许改这个字段。

**理由**:
- 与 spec 描述对齐(spec 直接用"起始 URL",隐含在 config 上)
- 一个 config 对应一个目标站点,起始 URL 应当固化,避免"今天跑这站、明天跑那站"的语义混乱
- 创建配置时强制给起始 URL,可以让用户在被 `UrlGuard` 拒绝时(回环、协议非 http/https)立刻发现,比启动爬取时才发现友好

**替代方案**:
- 任务创建时按次传入:被"语义漂移"否决——同一 config 跑出不同结果难以解释
- 两个都支持:被"实现 + 测试复杂度"否决

### Decision 2: 任务自开 Playwright BrowserContext(用户拍板)

**选择**:`CrawlEngine.run(task)` 内 `Playwright.create().newContext().newPage()`,任务结束 `context.close()` + `playwright.close()`。M2.5 的 `BrowserSessionService` 单例完全不动。

**理由**:
- 与 M2.5 单例 0 耦合,任务期间用户继续用预览不冲突
- 任务结束释放 Chromium 资源,避免长期占用
- 异常路径(`context.close()`)易于管理

**替代方案**:
- 复用 BrowserSessionService 单例:被"任务期间预览被锁"否决
- 共用 Playwright 实例各自开 Page:`Playwright.create()` 调用昂贵(创建 Chromium 进程),N 个任务并发要 N 次创建不可行;虽然 Decision 4 锁了单任务,实现上也仍按"自管生命周期"做最简单

### Decision 3: 进度走 REST 轮询(用户拍板)

**选择**:前端 `taskStore` 用 `setInterval` 每 1.5s 调 `GET /api/v1/tasks/:id`,直到 `status` 离开 `RUNNING`。

**理由**:
- 无 WebSocket 协议负担(M3 已经为"按模板预览"加了 WS 消息,M4 不再叠加)
- 后端无状态,实现简单
- 1.5s 粒度对单任务场景足够

**替代方案**:
- 新增独立 `/ws/tasks` WS:被"协议负担 + 心跳/重连"否决
- 复用 `/ws/page` 加 `taskProgress` 消息:被"协议语义混淆"否决
- Server-Sent Events:与项目风格不一致

### Decision 4: 同时只允许 1 个 RUNNING 任务(用户拍板)

**选择**:`CrawlEngine` 持 `AtomicReference<RunningTask>`,`submit(task)` 时 CAS 置入,遇非空抛 `TaskAlreadyRunningException`(HTTP 409,code=4090);任务结束(无论 COMPLETED/FAILED)清空引用。

**理由**:
- 与 Decision 2 配套:每个任务独占 BrowserContext,简单线性
- 与用户"不做分布式"基调对齐
- 进程内锁即可,无需持久化;服务重启时锁自动清空,由 zombie 清理 runner 善后

**替代方案**:
- ThreadPoolExecutor 限并发 2~3:被"实现复杂 + 资源消耗"否决
- 同 config 互斥 / 跨 config 并行:被"边界条件最复杂"否决

### Decision 5: 失败信息两层粒度(用户拍板)

**选择**:`crawl_task.error_message` 仅在状态 FAILED 或被外部停止时填(场景:`start_url` 经 UrlGuard 失败、配置被并发删除、顶层 try/catch 兜住);`article.error_message` / `list_item.error_message` / `detail_url.error_message` 记录该项的浏览器导航异常 / `page.evaluate` 异常 / 抽取异常。

**理由**:
- 用户最关心"我提交的 5 个 URL 哪几个失败了、为什么"——article 级粒度正好回答
- task 级 summary 仅作为"任务是否整体出问题"的快速判断(例如配置被删)
- 不引入"字段级"或"单 selector 级"错误存储(预览阶段已经能看到了,M4 重复存无价值)

**替代方案**:
- 只存 article 层:被"task 总览需要额外聚合"否决
- 三层(再加字段级):被"存储膨胀"否决

### Decision 6: 任务删除级联清理(用户拍板)

**选择**:`DELETE /api/v1/tasks/:id` 走 JPA cascade,清空该任务的所有 `list_page` → `list_item` → `article` 与 `detail_url` → `article`。复用 M1 已有模式(`@OneToMany(cascade=ALL, orphanRemoval=true)`),在实体关系上声明即可,Service 层不必手写事务。

**理由**:
- 与 M1 配置删除级联字段一致,无需新模式
- 避免"任务删了但数据还在"的孤儿问题

**替代方案**:
- 只删任务记录,数据保留:被"孤儿数据"否决
- 加 `?keepData=true` 参数:本期不引入,留待真有需求时再开

### Decision 7: 服务启动 zombie 清理(用户拍板)

**选择**:`ZombieTaskCleanerRunner implements ApplicationRunner` 在 `@PostConstruct`/应用启动阶段执行:`UPDATE crawl_task SET status='FAILED', error_message='服务重启,任务中断', completed_at=now() WHERE status='RUNNING'`。

**理由**:
- 进程内锁在重启时丢失,但 DB 里 RUNNING 记录还在,会变成 UI 上永远"运行中"的鬼魂
- 一行 SQL 兜底,简单可解释
- 可通过配置 `crawl.engine.startup-cleanup-enabled=false` 关掉(便于开发期手动复现 zombie)

**替代方案**:
- 不清理:被"鬼魂任务"否决
- 自动恢复执行:被"复杂度 + 数据一致"否决

### Decision 8: LIST_DETAIL 流程的 list_item 失败同步

**选择**:`CrawlEngine` 在 LIST_DETAIL 模式下:对每个 list_item,先 `page.goto(detail_url)`,try/catch 包住 `goto` 与后续 `ExtractionService` 调用,任一失败 → `article.status=FAILED, article.error_message=<异常 message>`,**同步**把 `list_item.status=FAILED, list_item.error_message=<异常 message>`。DETAIL_ONLY 模式下,只更新 `detail_url.status`,不涉及 list_item。

**理由**:
- spec 暗示"list_item 状态"代表该项处理结果;若 article 失败但 list_item 还是 PENDING,UI 上看不出 list_item 经历了什么
- 同步更新让 list_item / article 两张表的 status 描述同一个事实

### Decision 9: custom_fields 列从 LIST 模板取 detail_url,从 DETAIL 模板取业务字段

**选择**:在 LIST_DETAIL 模式下,第一步把 LIST 模板应用于列表页 DOM,从 `detail_url` 字段的 `validatedValues[0]` 取第一个 URL(已由浏览器自动绝对化);后续 article 的 `custom_fields` 仅包含 DETAIL 模板下的业务字段(`title` / `author` / `date` 等),不含 `detail_url`。在 DETAIL_ONLY 模式下,article 的 `custom_fields` 仅含 DETAIL 模板字段。

**理由**:
- spec `data-persistence` "自定义字段存储" 描述的是用户为页面定义的字段,`detail_url` 是爬取框架的"内部字段"不是用户字段
- 导出 / 浏览 article 时不暴露 `detail_url`,避免重复(URL 已在 `article.url` 上)

**替代方案**:
- 把 `detail_url` 塞进 custom_fields:被"重复存储 + 业务/框架字段混淆"否决

### Decision 10: 导出列集合 = 所有 article 的 custom_fields 键的并集

**选择**:`POST /api/v1/articles/export?format=JSON|xlsx&config_id=...&keyword=...` 在内存里聚合当前过滤结果的所有 `custom_fields` 键,作为列名;每篇文章缺失的列填 `null`(JSON)或空字符串(xlsx)。

**理由**:
- spec 原文:"所有文章共享同一列集合(所有键的并集)"
- xlsx 用 `org.apache.poi:poi-ooxml`,JSON 走 Jackson(已用),无新协议

### Decision 11: 前端任务页轮询 + 状态机

**选择**:`taskStore` state: `{ current: { id, status, totalItems, crawledItems, failedItems, ... } }`;action `startPolling(taskId)`:开 `setInterval(1500)`,`status` 离开 RUNNING 立即 `clearInterval`;同时支持 `stopPolling()`(路由切换时手动清)。

**理由**:
- 简单的请求/响应映射,无流式协议
- 离开页面立即停轮询,避免内存泄漏

### Decision 12: 启动爬取入口 — DETAIL_ONLY 弹 dialog, LIST_DETAIL 直发

**选择**:`ConfigList` / `ConfigEdit` 每行加 "启动爬取" 按钮。点击后:
- LIST_DETAIL → 直接 `POST /api/v1/tasks { configId }`
- DETAIL_ONLY → 弹 `StartCrawlDialog.vue`,多行 textarea 收集 URL(每行一个,自动 trim,过滤空行),提交时 `POST /api/v1/tasks { configId, urls: [...] }`

**理由**:
- DETAIL_ONLY 必须用户提供 URL(否则爬啥)
- LIST_DETAIL 的 start_url 已在 config 里,无附加输入
- URL 列表 UI 简单,无需文件上传(M4 不批量)

## Risks / Trade-offs

- **[风险 1] 单 Playwright 任务运行期间,服务重启 → 任务中断 → DB 里 RUNNING 卡住** → 由 Decision 7 zombie 清理 runner 兜底,UI 上能看到 error_message
- **[风险 2] 长任务(数百 article)期间,前端 1.5s 轮询累积请求** → 离开页面立即 stopPolling,后台任务不受影响;后端 `GET /tasks/:id` 走索引查询,响应 < 10ms
- **[风险 3] 任务失败时 article 抽取的 raw_html 已落库但 error_message 也填了** → 接受,raw_html 在 data-persistence spec 里"供后续重新解析",失败 article 也可被 M5 重新解析
- **[风险 4] `CrawlConfig.start_url` 字段 NOT NULL,无现网所以无回填** → M4 上线时新建配置必填;现网无此风险
- **[风险 5] `org.apache.poi:poi-ooxml` 新增依赖** → Apache 2.0 license,与项目一致;只用于导出,体积可控
- **[权衡 1] 单任务运行约束在长任务期间阻止用户启动其他任务** → 接受,UI 上以"运行中"状态明确提示;queueing 留 M5+
- **[权衡 2] article 错误信息粒度到 page.evaluate / 浏览器导航层,不到具体选择器层** → 接受,选择器级错误在预览阶段已经能看到
- **[权衡 3] 服务启动 zombie 清理默认开启,可能在开发期意外标 FAILED 正在手测的任务** → 配置开关 `crawl.engine.startup-cleanup-enabled` 暴露给开发期关掉

## Open Questions

无。所有关键模糊点(8 个)在 explore 阶段已通过 4 轮 AskUserQuestion 与用户拍板,本 design 不再引入新决策。
