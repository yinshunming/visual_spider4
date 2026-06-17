# crawl-execution（爬取执行）

## Purpose

爬取任务的完整生命周期管理，包括 LIST_DETAIL 和 DETAIL_ONLY 两种爬取流程、实时进度报告以及通过原子性停止标志实现的优雅中断。

## Requirements

### Requirement: 爬取任务生命周期
爬取任务代表一次爬取执行。系统 SHALL 允许创建、启动、停止、查询和删除任务。

#### 场景：创建 LIST_DETAIL 任务
- **WHEN** 用户为 LIST_DETAIL 项目创建任务
- **THEN** 系统创建状态为 RUNNING 的任务；total_items 在爬取过程中根据发现的列表项数量设置

#### 场景：创建 DETAIL_ONLY 任务并提供 URL 列表
- **WHEN** 用户为 DETAIL_ONLY 项目创建任务并提供 URL 列表
- **THEN** 系统创建状态为 RUNNING 的任务，并为每个 URL 创建一个 detail_url 记录

#### 场景：停止运行中的任务
- **WHEN** 用户对 RUNNING 状态的任务发送停止请求
- **THEN** 系统设置停止标志；爬取引擎在处理每个列表项或详情 URL 之间检查该标志并优雅退出；任务状态变为 COMPLETED（而非 FAILED）

#### 场景：任务成功完成
- **WHEN** 爬取任务完成所有项目处理且无错误
- **THEN** 任务状态变为 COMPLETED，completed_at 被设置，crawled_items 等于 total_items，failed_items 为 0

#### 场景：任务部分失败完成
- **WHEN** 爬取任务完成但部分项目失败
- **THEN** 任务状态变为 COMPLETED（部分成功视为 COMPLETED），completed_at 被设置，crawled_items + failed_items = total_items

### Requirement: LIST_DETAIL 爬取流程
爬取引擎 SHALL 按以下步骤执行 LIST_DETAIL 模式：

1. 在 Playwright 中加载起始 URL，等待 JS 渲染，将 raw_html 存入 list_page
2. 将 LIST 页面抽取模板应用于渲染后的 DOM → 生成 N 个 list_item 记录，每条包含 detail_url
3. 对每个 list_item（按顺序，检查停止标志）：
   a. 在 Playwright 中加载 detail_url，等待 JS 渲染，将 raw_html 存入 article
   b. 将 DETAIL 页面抽取模板应用于 article.custom_fields
   c. 将 list_item.status 更新为 CRAWLED

#### 场景：LIST_DETAIL 爬取 3 个列表项
- **WHEN** LIST_DETAIL 任务启动并发现 3 个列表项
- **THEN** 恰好创建 3 篇文章，每篇关联到其来源 list_item

### Requirement: DETAIL_ONLY 爬取流程
爬取引擎 SHALL 按以下步骤执行 DETAIL_ONLY 模式：

1. 对每个 detail_url 记录（按顺序，检查停止标志）：
   a. 在 Playwright 中加载 URL，等待 JS 渲染，将 raw_html 存入 article
   b. 将 DETAIL 页面抽取模板应用于 article.custom_fields
   c. 将 detail_url.status 更新为 CRAWLED

#### 场景：DETAIL_ONLY 爬取 5 个 URL
- **WHEN** DETAIL_ONLY 任务启动并提供 5 个 URL
- **THEN** 恰好创建 5 篇文章

### Requirement: 实时任务进度
系统 SHALL 实时报告任务进度。前端可查询任务的当前状态（total_items、crawled_items、failed_items、status）。

#### 场景：进度反映即时状态
- **WHEN** 任务运行中，10 个项目中已爬取 7 个
- **THEN** GET /tasks/:id 返回 crawled_items = 7，total_items = 10

### Requirement: 原子性停止标志
爬取引擎 SHALL 使用 AtomicBoolean 停止标志以支持优雅中断。标志在处理每个 list_item 或 detail_url 之间检查。

#### 场景：爬取中途停止
- **WHEN** 在处理第 4 个（共 10 个）项目时收到停止请求
- **THEN** 引擎完成当前项目后检查标志，在开始第 5 个项目前退出；项目 1-4 状态为 CRAWLED，项目 5-10 状态为 PENDING

### Requirement: 起始 URL 由 crawl_config 承载并经 UrlGuard 校验
系统 SHALL 在 `CrawlConfig` 上持久化 `startUrl` 字段(对应 `crawl_config.start_url` 列,NOT NULL)。`POST /api/v1/configs` 与 `PUT /api/v1/configs/:id` 在持久化前 MUST 调用 `UrlGuard.validate(startUrl, "startUrl")`;校验失败 MUST 抛 `StartUrlInvalidException`(code=4007),任务创建前已保证 `start_url` 合法。

#### 场景: 缺少 start_url 创建配置被拒绝
- **WHEN** 用户 POST /api/v1/configs,body 中未带 startUrl 或 startUrl 为空
- **THEN** 系统返回错误,code=4007,message 含"startUrl"

#### 场景: start_url 通过 UrlGuard 校验
- **WHEN** 用户 POST /api/v1/configs,startUrl=`https://example.com/list`(协议 http(s)、非回环)
- **THEN** 系统创建配置成功

#### 场景: start_url 指向回环地址被拒绝
- **WHEN** 用户 POST /api/v1/configs,startUrl=`http://127.0.0.1:8080` 或 `http://localhost:8080`
- **THEN** 系统返回错误,code=4007,message 含"回环"

### Requirement: 全局同时只允许 1 个 RUNNING 任务
系统 SHALL 在 `CrawlEngine` 进程内维护单一运行槽(进程内锁)。`POST /api/v1/tasks` 在调度执行前 MUST 申请该锁;已有 RUNNING 任务时新任务 MUST 立即返回 409,code=4090(`TaskAlreadyRunningException`),不进入调度队列。

#### 场景: 同时存在 RUNNING 任务时新任务被拒
- **WHEN** 全局已有 1 个 RUNNING 任务
- **AND** 用户 POST /api/v1/tasks 创建新任务
- **THEN** 系统返回 409,code=4090,message="已有任务在运行"

#### 场景: 任务正常结束后锁释放
- **WHEN** RUNNING 任务转为 COMPLETED 或 FAILED
- **THEN** 全局锁释放,新任务可被接受

### Requirement: 服务启动时 zombie 任务清理
系统 SHALL 在应用启动阶段运行 `ZombieTaskCleanerRunner`,将所有 `status='RUNNING'` 的 `crawl_task` 记录批量更新为 `status='FAILED'`、`error_message='服务重启,任务中断'`、`completed_at=now()`。该行为由 `crawl.engine.startup-cleanup-enabled` 配置控制(默认 true)。

#### 场景: 启动时 RUNNING 任务被标 FAILED
- **WHEN** 服务重启前 DB 内有 1 条 status=RUNNING 的 crawl_task
- **AND** 服务启动阶段 runner 执行
- **THEN** 该任务 status 变为 FAILED,error_message 填"服务重启,任务中断",completed_at 不为 null

#### 场景: 启动清理可关闭
- **WHEN** `crawl.engine.startup-cleanup-enabled=false`
- **THEN** runner 不执行,DB 中 RUNNING 任务保持原样(便于开发期手动复现 zombie)

### Requirement: LIST_DETAIL 模式下 article 失败同步 list_item 状态
在 LIST_DETAIL 模式下,系统 SHALL 在每个 list_item 处理结束时把 article 的 status 同步反映到 list_item:article.status=FAILED → list_item.status=FAILED,article.error_message 同步写入 list_item.error_message;article.status=CRAWLED → list_item.status=CRAWLED。

#### 场景: 详情页抽取失败时 list_item 同步标 FAILED
- **WHEN** LIST_DETAIL 任务处理某 list_item,article 抽取因 page.evaluate 异常失败
- **THEN** article.status=FAILED,list_item.status 同步为 FAILED,error_message 同步写入 list_item.error_message

#### 场景: 详情页抽取成功时 list_item 同步标 CRAWLED
- **WHEN** LIST_DETAIL 任务处理某 list_item,article 抽取成功
- **THEN** article.status=CRAWLED,list_item.status 同步为 CRAWLED

### Requirement: detail_url 不入 article.custom_fields
在 LIST_DETAIL 与 DETAIL_ONLY 模式下,系统 SHALL 不把 `detail_url` 字段值写入 `article.custom_fields`。`article.url` 字段单独存储最终 URL(已由浏览器自动绝对化),`custom_fields` 仅包含用户在 DETAIL 模板下定义的业务字段(标题/作者/日期等)。

#### 场景: 导出 article 时不出现 detail_url 列
- **WHEN** 用户导出某 LIST_DETAIL config 的所有 article
- **THEN** 导出列集合(所有 article 的 custom_fields 键的并集)不包含 `detail_url`

### Requirement: article 关联回 list_item 或 detail_url
`article` 实体 SHALL 包含两个可空外键:`list_item_id` 与 `detail_url_id`。LIST_DETAIL 模式下创建的 article 必填 `list_item_id`,`detail_url_id` 为 null;DETAIL_ONLY 模式下创建的 article 必填 `detail_url_id`,`list_item_id` 为 null。约束由 service 层在创建时强制,DB 层不做 NOT NULL(避免 JPA 反向关系复杂化)。

#### 场景: LIST_DETAIL 创建的 article 有 list_item_id
- **WHEN** LIST_DETAIL 任务创建 article
- **THEN** article.list_item_id 非空,article.detail_url_id 为 null

#### 场景: DETAIL_ONLY 创建的 article 有 detail_url_id
- **WHEN** DETAIL_ONLY 任务创建 article
- **THEN** article.detail_url_id 非空,article.list_item_id 为 null
