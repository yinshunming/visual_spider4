# crawl-execution (M4 增量)

> 本文件是 `implement-manual-crawl-execution` change 的 spec delta,与 `openspec/specs/crawl-execution/spec.md` 合并后形成 M4 完成态。

## ADDED Requirements

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
