# project-management

## 新增需求

### 需求：CrawlConfig 实体支持创建时设置 name、pageType、selectorType，默认状态为 STOPPED

#### 场景：使用有效数据创建新配置
- **WHEN** 用户发送 POST /api/v1/configs，包含有效的 name、pageType、selectorType
- **THEN** 系统创建 CrawlConfig，status=STOPPED，createdAt 和 updatedAt 自动设置
- **AND** 返回 201 Created，包含生成 id 的 ConfigResponse

#### 场景：使用 LIST_DETAIL pageType 创建配置
- **WHEN** 用户发送 POST /api/v1/configs，pageType=LIST_DETAIL
- **THEN** 系统创建配置，pageType=LIST_DETAIL

#### 场景：使用 DETAIL_ONLY pageType 创建配置
- **WHEN** 用户发送 POST /api/v1/configs，pageType=DETAIL_ONLY
- **THEN** 系统创建配置，pageType=DETAIL_ONLY

### 需求：CrawlConfig 实体支持 CSS 和 XPATH 选择器类型

#### 场景：使用 CSS 选择器创建配置
- **WHEN** 用户发送 POST /api/v1/configs，selectorType=CSS
- **THEN** 系统创建配置，selectorType=CSS

#### 场景：使用 XPATH 选择器创建配置
- **WHEN** 用户发送 POST /api/v1/configs，selectorType=XPATH
- **THEN** 系统创建配置，selectorType=XPATH

### 需求：CrawlConfig 列表查询返回分页结果

#### 场景：查询所有配置（无过滤条件）
- **WHEN** 用户发送 GET /api/v1/configs
- **THEN** 系统返回按 createdAt DESC 排序的分页配置列表

#### 场景：配置列表为空时返回空分页
- **WHEN** 用户发送 GET /api/v1/configs
- **AND** 数据库中不存在任何配置
- **THEN** 系统返回空分页，totalElements=0

### 需求：CrawlConfig 可通过 id 获取详情及关联字段

#### 场景：根据 id 获取配置
- **WHEN** 用户发送 GET /api/v1/configs/:id
- **THEN** 系统返回 ConfigResponse，包含所有字段
- **AND** 字段按 createdAt ASC 排序

#### 场景：根据不存在的 id 获取配置
- **WHEN** 用户发送 GET /api/v1/configs/99999
- **THEN** 系统返回 404 Not Found，包含错误信息

### 需求：CrawlConfig 更新时原子性替换所有字段

#### 场景：使用新字段列表更新配置
- **WHEN** 用户发送 PUT /api/v1/configs/:id，body 包含 fields=[...]
- **THEN** 系统删除该配置的所有现有字段
- **AND** 根据请求体创建新字段
- **AND** 返回更新后的 ConfigResponse

### 需求：CrawlConfig 删除时级联删除关联字段

#### 场景：删除有关联字段的配置
- **WHEN** 用户发送 DELETE /api/v1/configs/:id
- **THEN** 系统删除所有关联的 CrawlField 记录
- **AND** 删除 CrawlConfig 记录
- **AND** 返回 204 No Content

### 需求：CrawlField 实体作为 CrawlConfig 的子资源创建

#### 场景：为配置添加字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，包含 fieldName、fieldType、selector、pageType
- **THEN** 系统创建关联到该配置的 CrawlField
- **AND** 返回 201 Created，包含 FieldResponse

#### 场景：添加 TEXT 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=TEXT
- **THEN** 系统创建 fieldType=TEXT 的字段

#### 场景：添加 URL 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=URL
- **THEN** 系统创建 fieldType=URL 的字段

#### 场景：添加 NUMBER 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=NUMBER
- **THEN** 系统创建 fieldType=NUMBER 的字段

#### 场景：添加 DATE 类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，fieldType=DATE
- **THEN** 系统创建 fieldType=DATE 的字段

### 需求：CrawlField 实体支持 LIST 和 DETAIL 页面类型

#### 场景：添加 LIST 页面类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，pageType=LIST
- **THEN** 系统创建 pageType=LIST 的字段

#### 场景：添加 DETAIL 页面类型字段
- **WHEN** 用户发送 POST /api/v1/configs/:id/fields，pageType=DETAIL
- **THEN** 系统创建 pageType=DETAIL 的字段

### 需求：CrawlField 可通过 id 更新

#### 场景：更新现有字段
- **WHEN** 用户发送 PUT /api/v1/fields/:id，包含更新的 fieldName、fieldType、selector、pageType
- **THEN** 系统更新 CrawlField 记录
- **AND** 返回更新后的 FieldResponse

### 需求：CrawlField 可通过 id 删除

#### 场景：删除现有字段
- **WHEN** 用户发送 DELETE /api/v1/fields/:id
- **THEN** 系统删除 CrawlField 记录
- **AND** 返回 204 No Content

### 需求：CrawlField 列表可通过配置子资源访问

#### 场景：获取配置的字段列表
- **WHEN** 用户发送 GET /api/v1/configs/:id/fields
- **THEN** 系统返回该配置关联的字段列表
- **AND** 字段按 createdAt ASC 排序

### 需求：前端 ConfigList 页面展示分页配置列表

#### 场景：加载配置列表页面
- **WHEN** 用户导航到 /configs
- **THEN** 页面展示配置表格，列：name、pageType、selectorType、status
- **AND** 支持跳转到 /configs/new 新建配置
- **AND** 支持跳转到 /configs/:id 编辑配置

### 需求：前端 ConfigEdit 页面支持新建和编辑两种模式

#### 场景：新建配置
- **WHEN** 用户导航到 /configs/new
- **THEN** 页面显示空表单，包含 name、pageType、selectorType 字段
- **AND** 显示空字段列表用于添加字段
- **AND** 保存按钮通过 POST /api/v1/configs 创建配置

#### 场景：编辑现有配置
- **WHEN** 用户导航到 /configs/:id
- **THEN** 页面通过 GET /api/v1/configs/:id 加载配置数据
- **AND** 显示已填充的表单
- **AND** 显示关联字段列表
- **AND** 保存按钮通过 PUT /api/v1/configs/:id 更新配置

### 需求：前端 ConfigEdit 页面支持字段管理

#### 场景：在编辑页面添加字段
- **WHEN** 用户点击字段列表的"添加字段"按钮
- **THEN** 显示新的字段行，包含 fieldName、fieldType、selector、pageType 输入框
- **AND** 表单保存时通过 POST /api/v1/configs/:id/fields 创建字段

#### 场景：在编辑页面删除字段
- **WHEN** 用户点击字段行的删除按钮
- **THEN** 字段标记为待删除
- **AND** 表单保存时通过 DELETE /api/v1/fields/:id 删除字段

#### 场景：在编辑页面更新字段
- **WHEN** 用户修改字段值并点击保存
- **THEN** 更新的字段通过 PUT /api/v1/fields/:id 更新
- **AND** 新字段通过 POST /api/v1/configs/:id/fields 创建
