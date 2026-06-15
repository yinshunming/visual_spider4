# extraction-template（抽取模板）

## Purpose

定义和管理抽取模板的结构与行为，包括模板的独立拆分（LIST/DETAIL）、渲染后 DOM 提取、多值处理、类型校验以及 raw_html 重新解析能力。

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

### Requirement: 按模板批量提取协议(WebSocket previewTemplate)

系统 SHALL 通过 WebSocket `/api/v1/ws/page` 通道接收客户端 `{type:"previewTemplate", payload:{pageType:"LIST"|"DETAIL"}}` 消息，在当前活跃 Playwright Page 上，加载该 WebSocket session 已绑定的 `configId` 对应配置，过滤出指定 `pageType` 下的所有 `crawl_field` 字段，**对每个字段独立执行选择器并校验**，最终通过 `{type:"previewTemplateResult", payload:{fields:[...], warnings:[...]}}` 一次性返回结果。返回的 `fields` 数组 MUST 与字段在数据库中的 `created_at ASC` 顺序一致。

#### Scenario: 模板存在 3 个字段全部成功命中

- **WHEN** 客户端发送 `{type:"previewTemplate", payload:{pageType:"DETAIL"}}`，该 config 在 DETAIL 下有 3 个字段且选择器全部命中且类型校验通过
- **THEN** 服务端返回 `{type:"previewTemplateResult", payload:{fields:[<3 项>], warnings:[]}}`，每项的 `status="OK"`，`matchCount>=1`，`rawValues` 与 `validatedValues` 同长度且对应位置相等

#### Scenario: 当前会话无活跃 Page

- **WHEN** 客户端在 BrowserSession 未打开或已关闭时发送 `previewTemplate`
- **THEN** 服务端返回 `{type:"error", payload:{code:"NO_SESSION", message:"浏览器未就绪"}}`，**不返回 previewTemplateResult**

#### Scenario: 当前会话未绑定 configId

- **WHEN** 客户端在未先发送 `load` (含 configId) 的情况下直接发送 `previewTemplate`
- **THEN** 服务端返回 `{type:"error", payload:{code:"BAD_REQUEST", message:"请先发送 load 消息并携带 configId"}}`

### Requirement: 字段级状态四态语义

系统 SHALL 为每个字段独立返回 `status` 枚举，取值为 `OK` / `TYPE_MISMATCH` / `NO_MATCH` / `SELECTOR_INVALID` 之一，语义如下：

- `OK`：选择器在 Page DOM 上命中至少 1 个元素，且**所有**抽取值通过对应 `field_type` 的类型校验
- `TYPE_MISMATCH`：选择器命中至少 1 个元素，但**至少有 1 个**抽取值无法通过类型校验。失败位置在 `validatedValues` 中为 `null`，在 `rawValues` 中保留原始字符串。`message` SHALL 包含人类可读的失败原因（例如"非绝对 URL"、"非 ISO 8601 日期"）
- `NO_MATCH`：选择器在 Page DOM 上命中 0 个元素。`rawValues=[]`，`validatedValues=[]`，`matchCount=0`
- `SELECTOR_INVALID`：Page.evaluate 执行选择器时抛出异常（语法错误等）。`rawValues=[]`，`validatedValues=[]`，`matchCount=0`，`message` 包含 evaluate 抛出的错误描述

每个字段 result 项的形态固定为：`{fieldId, fieldName, fieldType, selector, matchCount, rawValues, validatedValues, status, message?}`，其中 `message` 仅在 `status != OK` 时出现。

#### Scenario: TEXT 字段命中且非空

- **WHEN** 字段 `field_type=TEXT`，选择器命中 1 个 textContent 为 "Hello" 的元素
- **THEN** 返回 `status="OK", matchCount=1, rawValues=["Hello"], validatedValues=["Hello"]`

#### Scenario: NUMBER 字段命中但值非数字

- **WHEN** 字段 `field_type=NUMBER`，选择器命中 1 个 textContent 为 "abc" 的元素
- **THEN** 返回 `status="TYPE_MISMATCH", matchCount=1, rawValues=["abc"], validatedValues=[null], message` 含"非数字"语义

#### Scenario: 选择器无匹配

- **WHEN** 字段选择器在 Page DOM 上命中 0 个元素
- **THEN** 返回 `status="NO_MATCH", matchCount=0, rawValues=[], validatedValues=[]`

#### Scenario: 选择器语法非法

- **WHEN** 字段选择器为 `>>>broken<<<` 之类导致 querySelectorAll 抛错
- **THEN** 返回 `status="SELECTOR_INVALID", matchCount=0, rawValues=[], validatedValues=[]`, message` 含浏览器错误描述

### Requirement: 多值字段统一以数组返回

`rawValues` 与 `validatedValues` 字段 MUST 始终为数组形态，**即使选择器仅命中 1 个或 0 个元素**。`rawValues.length === matchCount`；`validatedValues.length === matchCount`（部分位置可能为 `null`，代表该位置类型校验失败）。

#### Scenario: 选择器命中 5 个元素全部合法

- **WHEN** 字段 `field_type=TEXT`，选择器命中 5 个非空 textContent 元素
- **THEN** 返回 `matchCount=5, rawValues.length=5, validatedValues.length=5`，所有元素与 `rawValues` 等值

#### Scenario: 选择器命中 1 个元素

- **WHEN** 字段选择器命中 1 个元素
- **THEN** `rawValues=["xxx"], validatedValues=["xxx"]`，**仍为数组**而非标量

#### Scenario: 命中 0 个元素

- **WHEN** 字段选择器命中 0 个元素
- **THEN** `rawValues=[], validatedValues=[]`

### Requirement: URL 字段优先取 element.href

系统 SHALL 在 `field_type=URL` 时，提取阶段优先读取元素 DOM 的 `.href` 属性（浏览器自动绝对化相对路径）。若元素无 `.href` 属性（例如 `<span>`、`<div>`），退回读取 `textContent.trim()`。其他 `field_type` 一律读取 `textContent.trim()`。

#### Scenario: URL 字段命中 a 标签 + 相对 href

- **WHEN** 字段 `field_type=URL`，选择器命中 `<a href="/article/123">`，当前 Page URL 为 `https://example.com/list`
- **THEN** `rawValues=["https://example.com/article/123"]`（浏览器 DOM `.href` 自动绝对化），`status="OK"`，`validatedValues=["https://example.com/article/123"]`

#### Scenario: URL 字段命中非链接元素

- **WHEN** 字段 `field_type=URL`，选择器命中 `<span>not a link</span>`
- **THEN** `rawValues=["not a link"]`（退回 textContent），URL 校验不通过，`status="TYPE_MISMATCH"`，`validatedValues=[null]`

### Requirement: 字段类型校验细则

系统 SHALL 严格按以下规则进行字段类型校验，非法值置 `null`：

- **TEXT**：`raw.trim()` 后为空字符串 → `NO_MATCH`（整个字段视为未命中，而非保留空字符串到 validatedValues）
- **NUMBER**：`Double.parseDouble(raw)` 不抛异常即合法。**不接受**千分位形态（如 `"1,234"` → 非法）
- **DATE**：能被 `DateTimeFormatter.ISO_DATE` 或 `DateTimeFormatter.ISO_DATE_TIME` 之一解析即合法。**严格 ISO 8601**，不做 fuzzy parse
- **URL**：`new URI(raw).isAbsolute()` 为 true 且 `scheme` 为 `http` 或 `https` 即合法。其他形态（相对路径、`mailto:`、`ftp://` 等）非法

#### Scenario: NUMBER 字段值含千分位

- **WHEN** 字段 `field_type=NUMBER`，选择器命中元素 textContent 为 "1,234"
- **THEN** `validatedValues=[null]`，`status="TYPE_MISMATCH"`

#### Scenario: DATE 字段值符合 ISO 8601

- **WHEN** 字段 `field_type=DATE`，选择器命中元素 textContent 为 "2026-06-12"
- **THEN** `validatedValues=["2026-06-12"]`，`status="OK"`

#### Scenario: DATE 字段值为常见非 ISO 格式

- **WHEN** 字段 `field_type=DATE`，选择器命中元素 textContent 为 "2026/06/12" 或 "Jun 12, 2026"
- **THEN** `validatedValues=[null]`，`status="TYPE_MISMATCH"`

#### Scenario: URL 字段值为 mailto

- **WHEN** 字段 `field_type=URL`，选择器命中元素的 href 为 `mailto:a@b.com`
- **THEN** `validatedValues=[null]`，`status="TYPE_MISMATCH"`

### Requirement: 空模板容错

当 config 在指定 `pageType` 下定义的字段数量为零时，系统 SHALL 返回 `fields:[]` 与 `warnings:["该模板未定义任何 <pageType> 字段"]`，**不视为错误**，HTTP / WebSocket 响应正常返回。

#### Scenario: DETAIL 模板零字段触发预览

- **WHEN** config 的 DETAIL 字段为 0，客户端发送 `previewTemplate(pageType=DETAIL)`
- **THEN** 服务端返回 `{type:"previewTemplateResult", payload:{fields:[], warnings:["该模板未定义任何 DETAIL 字段"]}}`

### Requirement: raw_html 重新解析在本 change 显式不实现

系统 SHALL NOT 在本 change（M3 抽取模板预览）实现"对 list_page / article 等实体的 raw_html 重新解析"。`extraction-template` spec 中已存在的"从 raw_html 重新解析"Requirement 依赖 M4 (`crawl-execution` 与 `data-persistence`) 提供的爬取产物作为前置条件；本期数据库 schema 不包含 list_page / article / raw_html 字段，故该 Requirement 留待 M4 实现。本条目仅声明范围边界，**不**修改或撤销原 spec 中"从 raw_html 重新解析"Requirement 本身。

#### Scenario: 用户在 M3 尝试触发 raw_html 重新解析

- **WHEN** 用户尝试调用任何"重新解析"接口或菜单
- **THEN** 系统 MUST 不提供任何"重新解析"入口（无 REST、无 WebSocket 消息、无前端按钮），用户无法触发该流程
