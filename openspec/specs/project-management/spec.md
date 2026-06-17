# project-management

## Purpose

爬虫项目（配置）的全生命周期管理，包括创建、编辑、删除、查询、字段管理，以及页面类型模式、选择器类型、运行时状态控制。本能力提供后端 REST API 和前端配置管理 UI。
## Requirements
### Requirement: 项目（站点）配置生命周期

系统 SHALL 允许用户创建、读取、更新和删除爬虫项目（以下简称"配置"）。每个配置代表一个目标站点的爬取设置，由 CrawlConfig 实体承载（id、name、startUrl、pageType、selectorType、status、createdAt、updatedAt）。`startUrl` 为爬取起始 URL，LIST_DETAIL 模式下作为列表页入口，DETAIL_ONLY 模式下作为默认详情页候选（实际爬取 URL 由任务创建时提供的 urls 列表决定，见 [crawl-execution](../crawl-execution/spec.md)）。

#### Scenario: 创建新项目
- **WHEN** 用户提供项目名称（name）、起始 URL（startUrl）、页面类型（LIST_DETAIL 或 DETAIL_ONLY）、选择器类型（CSS 或 XPATH）
- **THEN** 系统创建项目，status 默认设为 STOPPED，并返回项目 ID；startUrl 经 UrlGuard 校验（详见 crawl-execution spec），缺失或为空时返回 code=4007

#### Scenario: 更新项目
- **WHEN** 用户修改现有项目的任何字段（name、startUrl、pageType、selectorType、status）
- **THEN** 系统更新项目，并将 updatedAt 设为当前时间戳

#### Scenario: 删除项目
- **WHEN** 用户删除一个项目
- **THEN** 系统级联删除所有关联的字段定义（CrawlField）

#### Scenario: 查询项目列表
- **WHEN** 用户请求所有项目
- **THEN** 系统返回分页列表，每条记录包含 id、name、pageType、selectorType、status、createdAt、updatedAt

### Requirement: 页面类型模式

系统 SHALL 支持两种互斥的页面类型模式（由 CrawlConfig.pageType 枚举表达）。

#### Scenario: 选择 LIST_DETAIL 模式
- **WHEN** 配置的 pageType 为 LIST_DETAIL
- **THEN** 系统支持同时定义 LIST 页面字段和 DETAIL 页面字段；爬取流程为：列表页 → 多个列表项 → 详情页

#### Scenario: 选择 DETAIL_ONLY 模式
- **WHEN** 配置的 pageType 为 DETAIL_ONLY
- **THEN** 系统只要求定义 DETAIL 页面字段

### Requirement: 项目状态

配置 SHALL 拥有以下状态之一：ACTIVE（运行中）或 STOPPED（已停用），由 ConfigStatus 枚举表达。状态作为运行时标志，不影响 API 操作。**新建配置的 status 默认值为 STOPPED**（不自动启动）。

#### Scenario: 停用项目
- **WHEN** 用户将项目状态设置为 STOPPED
- **THEN** 新任务不得启动该项目；进行中的任务继续运行直至自然结束

### Requirement: 系统 SHALL 允许创建 CrawlConfig（默认 status=STOPPED）

系统 SHALL 支持通过 POST /api/v1/configs 创建配置，name、startUrl、pageType、selectorType 为必填项；当 status 未显式传入时，系统 MUST 将其默认设为 STOPPED；createdAt 与 updatedAt MUST 由系统在持久化时自动填充。ConfigResponse 响应体 MUST 包含 startUrl 字段。

#### Scenario: 使用有效数据创建新配置
- **WHEN** 用户发送 POST /api/v1/configs，包含有效的 name、startUrl、pageType、selectorType
- **THEN** 系统创建 CrawlConfig，status=STOPPED，createdAt 和 updatedAt 自动设置
- **AND** 返回 201 Created，包含生成 id 与 startUrl 的 ConfigResponse

#### Scenario: 使用 LIST_DETAIL pageType 创建配置
- **WHEN** 用户发送 POST /api/v1/configs，pageType=LIST_DETAIL
- **THEN** 系统创建配置，pageType=LIST_DETAIL

#### Scenario: 使用 DETAIL_ONLY pageType 创建配置
- **WHEN** 用户发送 POST /api/v1/configs，pageType=DETAIL_ONLY
- **THEN** 系统创建配置，pageType=DETAIL_ONLY

### Requirement: 系统 SHALL 支持 CSS 和 XPATH 两种 selectorType

系统 SHALL 接受 selectorType=CSS 或 selectorType=XPATH 的创建/更新请求。

#### Scenario: 使用 CSS 选择器创建配置
- **WHEN** 用户发送 POST /api/v1/configs，selectorType=CSS
- **THEN** 系统创建配置，selectorType=CSS

#### Scenario: 使用 XPATH 选择器创建配置
- **WHEN** 用户发送 POST /api/v1/configs，selectorType=XPATH
- **THEN** 系统创建配置，selectorType=XPATH

### Requirement: 列表查询 SHALL 返回分页结果

GET /api/v1/configs SHALL 返回分页配置列表；返回的 Page MUST 包含 content、totalElements、totalPages 等标准字段。

#### Scenario: 查询所有配置（无过滤条件）
- **WHEN** 用户发送 GET /api/v1/configs
- **THEN** 系统返回分页配置列表

#### Scenario: 配置列表为空时返回空分页
- **WHEN** 用户发送 GET /api/v1/configs
- **AND** 数据库中不存在任何配置
- **THEN** 系统返回空分页，totalElements=0

### Requirement: 系统 SHALL 通过 id 返回配置详情及关联字段

GET /api/v1/configs/{id} SHALL 返回 ConfigResponse，其中 MUST 包含该配置的所有 CrawlField（按 createdAt ASC 排序）；当 id 不存在时系统 MUST 返回 code=404 错误。

#### Scenario: 根据 id 获取配置
- **WHEN** 用户发送 GET /api/v1/configs/:id
- **THEN** 系统返回 ConfigResponse，包含所有字段
- **AND** 字段按 createdAt ASC 排序

#### Scenario: 根据不存在的 id 获取配置
- **WHEN** 用户发送 GET /api/v1/configs/99999
- **THEN** 系统返回 404 Not Found，包含错误信息

### Requirement: 系统 SHALL 在 PUT 配置时原子性替换所有字段

PUT /api/v1/configs/{id} SHALL 在单个事务内清空该配置的所有现有 CrawlField，并根据请求体中的 fields 数组创建新字段；中间状态 MUST 不可见。

#### Scenario: 使用新字段列表更新配置
- **WHEN** 用户发送 PUT /api/v1/configs/:id，body 包含 fields=[...]
- **THEN** 系统删除该配置的所有现有字段
- **AND** 根据请求体创建新字段
- **AND** 返回更新后的 ConfigResponse

### Requirement: 系统 SHALL 在删除配置时级联删除关联字段

DELETE /api/v1/configs/{id} SHALL 删除 CrawlConfig 记录并通过 JPA cascade 同步删除所有关联的 CrawlField 记录。

#### Scenario: 删除有关联字段的配置
- **WHEN** 用户发送 DELETE /api/v1/configs/:id
- **THEN** 系统删除所有关联的 CrawlField 记录
- **AND** 删除 CrawlConfig 记录
- **AND** 返回 204 No Content

### Requirement: 系统 SHALL 将 CrawlField 作为配置子资源创建

POST /api/v1/configs/{configId}/fields SHALL 创建关联到指定配置的 CrawlField；fieldType MUST 支持 TEXT / NUMBER / DATE / URL 四种取值。

#### Scenario: 为配置添加字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，包含 fieldName、fieldType、selector、pageType
- **THEN** 系统创建关联到该配置的 CrawlField
- **AND** 返回 201 Created，包含 FieldResponse

#### Scenario: 添加 TEXT 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=TEXT
- **THEN** 系统创建 fieldType=TEXT 的字段

#### Scenario: 添加 URL 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=URL
- **THEN** 系统创建 fieldType=URL 的字段

#### Scenario: 添加 NUMBER 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=NUMBER
- **THEN** 系统创建 fieldType=NUMBER 的字段

#### Scenario: 添加 DATE 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=DATE
- **THEN** 系统创建 fieldType=DATE 的字段

### Requirement: 系统 SHALL 支持 LIST 和 DETAIL 两种字段 pageType

CrawlField 的 pageType MUST 接受 LIST 或 DETAIL 两个枚举值。

#### Scenario: 添加 LIST 页面类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，pageType=LIST
- **THEN** 系统创建 pageType=LIST 的字段

#### Scenario: 添加 DETAIL 页面类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，pageType=DETAIL
- **THEN** 系统创建 pageType=DETAIL 的字段

### Requirement: 系统 SHALL 允许通过 id 更新 CrawlField

PUT /api/v1/fields/{id} SHALL 更新指定 CrawlField 的全部可变字段。

#### Scenario: 更新现有字段
- **WHEN** 用户发送 PUT /api/v1/fields/:id，包含更新的 fieldName、fieldType、selector、pageType
- **THEN** 系统更新 CrawlField 记录
- **AND** 返回更新后的 FieldResponse

### Requirement: 系统 SHALL 允许通过 id 删除 CrawlField

DELETE /api/v1/fields/{id} SHALL 删除指定的 CrawlField 记录。

#### Scenario: 删除现有字段
- **WHEN** 用户发送 DELETE /api/v1/fields/:id
- **THEN** 系统删除 CrawlField 记录
- **AND** 返回 204 No Content

### Requirement: 系统 SHALL 通过配置子资源列出字段

GET /api/v1/configs/{configId}/fields SHALL 返回该配置关联的字段列表，按 createdAt ASC 排序。

#### Scenario: 获取配置的字段列表
- **WHEN** 用户发送 GET /api/v1/configs/:id/fields
- **THEN** 系统返回该配置关联的字段列表
- **AND** 字段按 createdAt ASC 排序

### Requirement: 前端 ConfigList 页面 SHALL 展示分页配置列表

/vue/ConfigList 组件 SHALL 在 onMounted 触发 fetchConfigs，并通过 Element Plus el-table 展示 name、pageType、selectorType、status 列；新建按钮 SHALL 跳转到 /configs/new，编辑按钮 SHALL 跳转到 /configs/:id。

#### Scenario: 加载配置列表页面
- **WHEN** 用户导航到 /configs
- **THEN** 页面展示配置表格，列：name、pageType、selectorType、status
- **AND** 支持跳转到 /configs/new 新建配置
- **AND** 支持跳转到 /configs/:id 编辑配置

### Requirement: 前端 ConfigList SHALL 按页面类型提供爬取启动入口

/vue/ConfigList 的"启动爬取"按钮 SHALL 根据配置 pageType 采取不同交互：
- DETAIL_ONLY：点击后弹出 URL 输入对话框（StartCrawlDialog），用户每行输入一个详情 URL；提交后将 urls 数组随 POST /api/v1/tasks 一并创建任务。空 URL 列表 MUST 阻止提交并提示。
- LIST_DETAIL：点击后直接 POST /api/v1/tasks（urls=null），爬取从 config.startUrl 解析列表页。

任务创建成功后 SHALL 跳转到 /tasks/:id 详情页。

#### Scenario: DETAIL_ONLY 启动爬取需先输入 URL
- **WHEN** 用户点击 DETAIL_ONLY 配置的"启动爬取"
- **THEN** 弹出 URL 输入对话框，用户输入若干 URL 后提交
- **AND** 系统 POST /api/v1/tasks 携带 urls 数组创建任务并跳转详情页

#### Scenario: DETAIL_ONLY 空 URL 列表被阻止
- **WHEN** 用户在 URL 输入对话框中未输入任何 URL 即点启动
- **THEN** 前端提示"请至少输入一个 URL"，不发起创建请求

#### Scenario: LIST_DETAIL 启动爬取无需输入 URL
- **WHEN** 用户点击 LIST_DETAIL 配置的"启动爬取"
- **THEN** 系统 POST /api/v1/tasks（urls=null）创建任务并跳转详情页

### Requirement: 前端 ConfigEdit 页面 SHALL 同时支持新建和编辑模式

/vue/ConfigEdit SHALL 根据 route.params.id 区分两种模式；新建模式表单为空，编辑模式通过 GET /api/v1/configs/:id 加载并填充数据。

#### Scenario: 新建配置
- **WHEN** 用户导航到 /configs/new
- **THEN** 页面显示空表单，包含 name、startUrl、pageType、selectorType 字段
- **AND** 显示空字段列表用于添加字段
- **AND** 保存按钮通过 POST /api/v1/configs 创建配置（payload 携带 startUrl）

#### Scenario: 编辑现有配置
- **WHEN** 用户导航到 /configs/:id
- **THEN** 页面通过 GET /api/v1/configs/:id 加载配置数据
- **AND** 显示已填充的表单（含 startUrl 回填）
- **AND** 显示关联字段列表
- **AND** 保存按钮通过 PUT /api/v1/configs/:id 更新配置（fields 数组全量替换）

### Requirement: 前端 ConfigEdit 页面 SHALL 支持字段的添加、修改、删除

/vue/ConfigEdit SHALL 提供添加字段行、修改字段值、删除字段行的交互，保存时将整个 fields 数组通过 PUT /api/v1/configs/:id 提交给后端进行全量替换。

#### Scenario: 在编辑页面添加字段
- **WHEN** 用户点击字段列表的"添加字段"按钮
- **THEN** 显示新的字段行，包含 fieldName、fieldType、selector、pageType 输入框
- **AND** 表单保存时通过 PUT /api/v1/configs/:id 全量提交字段

#### Scenario: 在编辑页面删除字段
- **WHEN** 用户点击字段行的删除按钮
- **THEN** 字段从前端状态中移除
- **AND** 表单保存时通过 PUT /api/v1/configs/:id 全量提交（不包含已删除字段）

#### Scenario: 在编辑页面修改字段并保存
- **WHEN** 用户修改字段值并点击保存
- **THEN** PUT /api/v1/configs/:id 携带修改后的 fields 数组
- **AND** 后端原子替换并返回更新后的 ConfigResponse

