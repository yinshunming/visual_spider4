# selector-rule-management（选择器规则管理）

## Purpose

定义和管理抽取字段的结构化规则，包括字段名、类型和选择器。字段按页面类型（LIST/DETAIL）分组，支持点击生成选择器的可视化辅助。

## Requirements

### Requirement: 按页面类型的字段定义
对于每个配置，用户 SHALL 为每种页面类型（LIST 页面和 DETAIL 页面）定义零个或多个字段。每个字段包含：field_name（用户自定义文本）、field_type（TEXT | NUMBER | DATE | URL）、selector（CSS 或 XPath 字符串）。

#### 场景：为 LIST 页面添加字段
- **WHEN** 用户为 LIST 页面添加字段（名称、类型、选择器）
- **THEN** 系统存储字段定义，关联到项目和 page_type = LIST

#### 场景：为 DETAIL 页面添加字段
- **WHEN** 用户为 DETAIL 页面添加字段（名称、类型、选择器）
- **THEN** 系统存储字段定义，关联到项目和 page_type = DETAIL

#### 场景：更新字段
- **WHEN** 用户修改现有字段定义（名称、类型或选择器）
- **THEN** 系统更新字段记录；不影响已有的爬取数据

#### 场景：删除字段
- **WHEN** 用户删除字段定义
- **THEN** 系统删除字段记录；不影响已有的爬取数据

### Requirement: 选择器类型约束
配置 SHALL 以单一选择器模式运行：CSS 或 XPATH。配置内所有字段必须使用该模式指定的语言。切换选择器类型会使所有已有选择器失效（因为它们是按另一种模式生成的）。

#### 场景：CSS 模式项目
- **WHEN** 配置的 selector_type 为 CSS
- **THEN** 所有字段选择器均作为 CSS 选择器解释

#### 场景：XPath 模式项目
- **WHEN** 配置的 selector_type 为 XPATH
- **THEN** 所有字段选择器均作为 XPath 表达式解释

### Requirement: detail_url 字段（仅 LIST 页面）
对于 LIST_DETAIL 模式配置，系统 SHALL 必填有一个名为 "detail_url"、field_type = URL 的 LIST 页面字段。该字段为必填，用于从每个列表项中提取详情页 URL。

#### 场景：缺少 detail_url 字段
- **WHEN** LIST_DETAIL 项目的 LIST 页面中没有名为 detail_url 且类型为 URL 的字段
- **THEN** 系统 SHALL 阻止启动该项目的爬取任务，并返回验证错误

### Requirement: 点击生成选择器并用于字段
选择器 SHALL 依据用户点击浏览器页面自动生成（详见 page-visual-selection），并可直接用于字段定义，无需手动编辑。

#### 场景：使用生成的选择器填充字段
- **WHEN** 用户点击了元素并收到了生成的选择器
- **THEN** 用户可将 CSS 或 XPath 结果赋值给任意字段的选择器
