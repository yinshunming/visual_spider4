# crawl-execution（爬取执行）

## ADDED Requirements

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
