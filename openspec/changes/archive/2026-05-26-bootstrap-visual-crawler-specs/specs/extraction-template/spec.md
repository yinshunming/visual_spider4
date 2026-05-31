# extraction-template（抽取模板）

## ADDED Requirements

### Requirement: 抽取模板结构
系统 SHALL 为每个配置定义两个独立的抽取模板：LIST 页面字段模板和 DETAIL 页面字段模板。每个模板是该页面类型的所有字段定义的集合。在爬取执行期间，模板应用于该类型的每个页面。

#### 场景：LIST 模板应用于所有列表页
- **WHEN** 爬取任务执行并遇到列表页
- **THEN** 系统 SHALL 将所有 LIST 页面字段选择器应用于渲染后的 DOM，为每个列表项提取每字段一个值

#### 场景：DETAIL 模板应用于所有详情页
- **WHEN** 爬取任务执行并遇到详情页
- **THEN** 系统 SHALL 将所有 DETAIL 页面字段选择器应用于渲染后的 DOM，为每字段提取一个值

### Requirement: 从渲染后的 DOM 提取字段
所有字段提取 SHALL 对完全渲染后的 DOM（在 JS 执行完成后）执行。静态 HTML 解析（Jsoup）仅用于重新解析先前存储的 raw_html，不作为爬取期间的主要提取路径。

#### 场景：从 JS 渲染后的 DOM 提取
- **WHEN** 爬取期间应用字段选择器
- **THEN** 系统使用 Playwright 浏览器会话的实时 DOM（JS 渲染后）来提取字段值

### Requirement: 多值字段提取
字段选择器 MAY 匹配页面上的零个、一个或多个元素。系统 SHALL 提取所有匹配值并以 JSON 数组形式存储在对应的 custom_fields 条目中。

#### 场景：选择器匹配多个元素
- **WHEN** 某字段选择器匹配页面上的 N 个元素（N >= 0）
- **THEN** 系统为该字段提取一个包含 N 个值的数组

### Requirement: 字段类型校验
提取后，每个字段值 SHALL 根据其声明的 field_type 进行校验：

- TEXT：任意非空字符串为有效值
- NUMBER：字符串必须可解析为数字；无效值存储为 null
- DATE：字符串必须符合 ISO 8601；无效值存储为 null
- URL：字符串必须是有效的绝对 URL；无效值存储为 null

#### 场景：有效的 TEXT 类型字段
- **WHEN** field_type 为 TEXT 且值为任意非空字符串
- **THEN** 值原样存储

#### 场景：无效的 NUMBER 类型字段
- **WHEN** field_type 为 NUMBER 且值为 "abc"
- **THEN** 该字段值存储为 null

#### 场景：无效的 URL 类型字段
- **WHEN** field_type 为 URL 且值为 "not-a-url"
- **THEN** 该字段值存储为 null

### Requirement: 字段集不完整
页面类型的字段定义可以为零个。系统 SHALL 允许零字段定义而不阻止爬取执行。

#### 场景：未定义 LIST 页面字段
- **WHEN** 配置的 LIST 页面字段为零
- **THEN** 爬取继续，不提取任何 LIST 页面数据；但 detail_url 字段仍需存在（任务启动时强制校验）

### Requirement: 从 raw_html 重新解析
系统 SHALL 支持使用 Jsoup 对先前爬取的列表页和文章进行重新解析，应用当前的抽取模板。

#### 场景：重新解析列表页
- **WHEN** 用户触发对某 list_page 实体重新解析
- **THEN** 系统加载存储的 raw_html，应用当前的 LIST 页面抽取模板，并更新关联的 list_item 记录

#### 场景：重新解析文章
- **WHEN** 用户触发对某 article 实体重新解析
- **THEN** 系统加载存储的 raw_html，应用当前的 DETAIL 页面抽取模板，并更新文章的 custom_fields
