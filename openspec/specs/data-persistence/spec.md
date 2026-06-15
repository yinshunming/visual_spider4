# data-persistence（数据持久化）

## Purpose

爬取结果的持久化存储，包括 raw_html 原始页面保留、custom_fields JSON 灵活存储、查询浏览、数据导出和实体级联删除。

## ADDED Requirements

### Requirement: 爬取数据实体存储
系统 SHALL 将爬取数据存储在以下实体中：list_page（每个访问的列表页一个）、list_item（从列表页提取的每个列表项一个）、article（每个访问的详情页一个）、detail_url（DETAIL_ONLY 模式中每个用户提供的 URL 一个）。所有实体保留爬取时的原始 HTML。

#### 场景：列表页保留 raw_html
- **WHEN** 爬取列表页
- **THEN** 系统将完整原始 HTML 内容存储在 list_page.raw_html 字段中，供后续重新解析

#### 场景：文章保留 raw_html
- **WHEN** 爬取详情页
- **THEN** 系统将完整原始 HTML 内容存储在 article.raw_html 字段中，供后续重新解析

### Requirement: 自定义字段存储
用户定义的字段值 SHALL 以 JSON 对象（custom_fields）存储在对应实体中。结构为扁平键值映射，键为用户定义的字段名。

#### 场景：为文章存储 custom_fields
- **WHEN** 提取产生字段值 {"文章标题": "Game 7 Preview", "作者": "John Doe", "发布日期": "2026-05-25"}
- **THEN** article.custom_fields JSON 列存储的正是该对象

### Requirement: 数据查询与浏览
系统 SHALL 提供只读 API 以浏览所有爬取数据，包括：list_pages（按 config_id 和 task_id 分页过滤）、list_items（按 list_page_id 分页过滤）、articles（按 config_id、task_id、关键词搜索 custom_fields 分页过滤）。

#### 场景：关键词搜索 + 分页查询文章列表
- **WHEN** 用户请求 GET /articles?config_id=1&keyword=warrior&page=1&size=20
- **THEN** 系统返回匹配 config_id=1 且任意 custom_fields 值包含 "warrior" 的文章，分页返回

#### 场景：按 list_page 浏览列表项
- **WHEN** 用户请求 GET /list-items?list_page_id=5
- **THEN** 系统返回属于列表页 5 的所有列表项

### Requirement: 数据导出
系统 SHALL 允许以 Excel (.xlsx) 和 JSON 格式导出文章数据。导出的每行对应一篇文章，每列对应一个 custom_fields 键。

#### 场景：导出为 JSON
- **WHEN** 用户请求 POST /articles/export?format=JSON
- **THEN** 系统返回所有文章（遵循当前过滤条件）的 JSON 数组，custom_fields 展开为顶级字段

#### 场景：导出为 Excel
- **WHEN** 用户请求 POST /articles/export?format=xlsx
- **THEN** 系统返回 Excel 文件，每行一篇文章，每列对应一个 custom_fields 键；所有文章共享同一列集合（所有键的并集）

### Requirement: 文章详情查看
系统 SHALL 提供单篇文章详情视图，展示文章 URL、状态、error_message（如有）、raw_html 和完整的 custom_fields 对象。

#### 场景：查看文章详情
- **WHEN** 用户请求 GET /articles/:id
- **THEN** 系统返回完整 article 实体，包括 raw_html 和 custom_fields

### Requirement: 实体删除
系统 SHALL 允许单独删除任意爬取数据实体（list_page、list_item、article）。

#### 场景：删除 list_page 级联到 list_items
- **WHEN** 用户删除 list_page
- **THEN** 所有关联的 list_items 也被删除（级联）

### Requirement: crawl_task 顶层 error_message 仅在失败时填
`crawl_task.error_message` SHALL 仅在以下场景被填充:
- 服务启动 zombie 清理(`error_message='服务重启,任务中断'`)
- 任务整体失败(顶层异常:`startUrl` 校验失败、配置被并发删除、`page.goto(startUrl)` 抛错等)
- 任务被用户手动停止(spec 已规定停止时 status 仍为 COMPLETED,本字段保持空)

正常 COMPLETED 状态的 `crawl_task.error_message` MUST 为 null。

#### 场景: 正常完成的任务 error_message 为空
- **WHEN** LIST_DETAIL 任务执行完所有 list_item 且无顶层异常
- **THEN** task.status=COMPLETED,task.error_message=null

#### 场景: 顶层异常时 task 变 FAILED 并填 error_message
- **WHEN** 任务执行 `page.goto(startUrl)` 抛 `PlaywrightException`
- **THEN** task.status=FAILED,task.error_message 含异常 message,completed_at 不为 null

#### 场景: zombie 任务的 error_message 固定文案
- **WHEN** 服务启动 runner 清理 RUNNING 任务
- **THEN** task.status=FAILED,task.error_message="服务重启,任务中断"

### Requirement: article / list_item / detail_url 记录各自错误
`article.error_message`、`list_item.error_message`、`detail_url.error_message` SHALL 独立记录该项的失败原因。可能的失败原因:
- `page.goto` 抛 `PlaywrightException`(导航失败)
- `ExtractionService.extractByTemplate` 内 `page.evaluate` 抛错(选择器语法非法等)
- 浏览器超时 / 上下文关闭

失败项 status MUST 标 FAILED,error_message 含失败原因(可为异常 message 或截断版本)。

#### 场景: 单个 article 抽取失败时 article 与 list_item 同步记错
- **WHEN** LIST_DETAIL 任务处理第 3 个 list_item,page.goto(detail_url) 超时
- **THEN** article.status=FAILED,article.error_message 含超时异常;list_item.status=FAILED,list_item.error_message 同 article

#### 场景: detail_url 抽取失败时 detail_url 记错
- **WHEN** DETAIL_ONLY 任务处理第 1 条 detail_url,page.goto 失败
- **THEN** detail_url.status=FAILED,detail_url.error_message 含失败原因;关联 article 同样 FAILED

### Requirement: 删除任务时级联清理所有爬取产物
`DELETE /api/v1/tasks/:id` SHALL 级联删除该任务下的全部:
- `list_page` 记录,连带其下 `list_item` 记录,连带 `list_item` 关联的 `article` 记录
- `detail_url` 记录,连带其关联的 `article` 记录

级联通过 JPA `@OneToMany(cascade=ALL, orphanRemoval=true)` 声明,由 service 在 `@Transactional` 内调 `repository.delete(task)` 触发。删除完成后 MUST 返回 204 No Content。

#### 场景: 删除 LIST_DETAIL 任务清空其下全部实体
- **WHEN** 用户 DELETE /api/v1/tasks/123
- **AND** task 123 下有 1 个 list_page、3 个 list_item、3 个 article
- **THEN** 1+3+3 = 7 条记录全部被删除,task 123 自身被删除,接口返回 204

#### 场景: 删除 DETAIL_ONLY 任务清空其下全部实体
- **WHEN** 用户 DELETE /api/v1/tasks/456
- **AND** task 456 下有 5 个 detail_url、5 个 article
- **THEN** 5+5+1 = 11 条记录全部被删除,接口返回 204

### Requirement: 导出列集合为所有 article.custom_fields 键的并集
`POST /api/v1/articles/export?format=JSON|xlsx[&config_id=...][&keyword=...]` SHALL 在导出时聚合当前过滤结果中所有 article 的 `custom_fields` 键,作为列名。每篇文章缺失某列时,JSON 导出填 `null`,xlsx 导出填空字符串。导出行顺序与分页查询保持一致。

#### 场景: JSON 导出含键并集与 null 填充
- **WHEN** 用户导出某 config 下 3 篇文章,文章 A 有键 {title, author},文章 B 有键 {title, date},文章 C 有键 {title}
- **THEN** 导出 JSON 数组的列集合 = {title, author, date};文章 A 的 date=null、文章 B 的 author=null、文章 C 的 author=null 且 date=null

#### 场景: xlsx 导出同样按并集列
- **WHEN** 同上场景,format=xlsx
- **THEN** xlsx 头行 = {title, author, date};每行单元格按"该文章有则填值,无则填空字符串"

### Requirement: 任务列表分页与过滤
`GET /api/v1/tasks?config_id=...&page=0&size=20` SHALL 返回该 config 下的分页任务列表(按 `started_at` DESC 排序);`config_id` 缺省时返回全部任务的全局列表。每条记录 MUST 包含 `id, configId, pageType, status, totalItems, crawledItems, failedItems, startedAt, completedAt, errorMessage`。

#### 场景: 按 config 过滤任务列表
- **WHEN** 用户 GET /api/v1/tasks?config_id=1&page=0&size=20
- **THEN** 系统返回 config_id=1 的所有任务,按 started_at DESC 排序,分页形态同 M1 已有 Page 结构

#### 场景: 全局任务列表
- **WHEN** 用户 GET /api/v1/tasks(无 config_id 过滤)
- **THEN** 系统返回所有任务,分页形态同上
