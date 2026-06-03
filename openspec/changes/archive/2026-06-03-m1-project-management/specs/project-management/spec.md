# project-management

## ADDED Requirements

### Requirement: 系统 SHALL 允许创建 CrawlConfig（默认 status=STOPPED）

系统 SHALL 支持通过 POST /api/v1/configs 创建配置，name、pageType、selectorType 为必填项；当 status 未显式传入时，系统 MUST 将其默认设为 STOPPED；createdAt 与 updatedAt MUST 由系统在持久化时自动填充。

#### Scenario: 使用有效数据创建新配置
- **WHEN** 用户发送 POST /api/v1/configs，包含有效的 name、pageType、selectorType
- **THEN** 系统创建 CrawlConfig，status=STOPPED，createdAt 和 updatedAt 自动设置
- **AND** 返回 201 Created，包含生成 id 的 ConfigResponse

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

### Requirement: 前端 ConfigEdit 页面 SHALL 同时支持新建和编辑模式

/vue/ConfigEdit SHALL 根据 route.params.id 区分两种模式；新建模式表单为空，编辑模式通过 GET /api/v1/configs/:id 加载并填充数据。

#### Scenario: 新建配置
- **WHEN** 用户导航到 /configs/new
- **THEN** 页面显示空表单，包含 name、pageType、selectorType 字段
- **AND** 显示空字段列表用于添加字段
- **AND** 保存按钮通过 POST /api/v1/configs 创建配置

#### Scenario: 编辑现有配置
- **WHEN** 用户导航到 /configs/:id
- **THEN** 页面通过 GET /api/v1/configs/:id 加载配置数据
- **AND** 显示已填充的表单
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
