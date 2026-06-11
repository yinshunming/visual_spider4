## ADDED Requirements

### Requirement: 点击元素生成 CSS 与 XPath 候选

系统 SHALL 在 WebSocket `/api/v1/ws/page` 通道接收客户端 `{type:"click", x:<int>, y:<int>}` 消息后,在当前 Playwright Page 上调用 `page.evaluate("document.elementFromPoint(x, y)")` 拿到目标元素,同时为该元素生成 CSS 与 XPath 两种候选选择器,并通过同一通道返回 `{type:"selectors", css:{selector,matchCount,samples}, xpath:{selector,matchCount,samples}}`。`matchCount` 是在当前已加载页面 DOM 上执行该选择器得到的元素数量;`samples` 是前 5 个匹配元素的 `textContent.trim()`(若元素无文本则取 `outerHTML` 前 80 字符)。CSS 候选 MUST 由 `css-selector-generator` 库生成;XPath 候选 MUST 由后端自写反推算法生成(沿 ancestor chain 拼轴与谓词)。若 `elementFromPoint` 返回 null(点击位置没有命中任何元素),系统 SHALL 返回 `{type:"error", code:"NO_ELEMENT", message:"未命中任何元素"}`。

#### Scenario: 点击命中普通 div
- **WHEN** 客户端发送 `{type:"click", x:120, y:80}`,该坐标命中一个 `<div class="title">示例</div>`
- **THEN** 服务端返回 `{type:"selectors", css:{selector:"div.title", matchCount:>=1, samples:["示例"]}, xpath:{selector:"//div[@class='title']" 或等价轴+谓词, matchCount:>=1, samples:["示例"]}}`

#### Scenario: 点击未命中元素
- **WHEN** 客户端发送 `{type:"click", x:-10, y:-10}`(坐标越界)
- **THEN** 服务端返回 `{type:"error", code:"NO_ELEMENT", message:"未命中任何元素"}`

#### Scenario: CSS 与 XPath 候选同时返回
- **WHEN** 服务端返回 selectors 消息
- **THEN** `css` 与 `xpath` 两个字段 MUST 都不为 null,即使其中一个候选无法生成(无法生成时该字段 MUST 缺失并 message 含"无法生成"提示)

### Requirement: 匹配预览高亮

系统 SHALL 在收到 `{type:"preview", selectorType:"css"|"xpath", selector:<string>}` 消息后,在当前 Page 上 `page.evaluate` 执行该选择器,对所有匹配元素添加 `class="vs-highlight"`,样式 `outline:2px solid #ff4d4f;background:rgba(255,77,79,0.15)`,然后重新截图并通过 `{type:"screenshot", data:"<base64 png>"}` 推回客户端。高亮注入 MUST 是幂等的(先 querySelectorAll(".vs-highlight") 移除旧高亮,再注入新高亮)。返回的消息中 MUST 同时包含 `{type:"previewResult", matchCount:<int>, samples:[<string>, ...]}`。`matchCount` 为 0 时,samples 为空数组。

#### Scenario: CSS 选择器匹配多个元素并高亮
- **WHEN** 客户端发送 `{type:"preview", selectorType:"css", selector:"div.item"}`,当前页面有 3 个 `div.item`
- **THEN** 服务端注入高亮,推送新截图帧,并返回 `{type:"previewResult", matchCount:3, samples:[<前 3 个 textContent>]}`

#### Scenario: 重复预览清除旧高亮
- **WHEN** 客户端先后发送两次 preview 消息,selectorA 匹配 2 个,selectorB 匹配 5 个
- **THEN** 第一次截图显示 2 个高亮;第二次截图 MUST 只显示 5 个,不再残留 selectorA 的高亮

#### Scenario: 匹配为空
- **WHEN** 客户端发送 `{type:"preview", selectorType:"css", selector:".no-such-class"}`
- **THEN** 服务端返回 `{type:"previewResult", matchCount:0, samples:[]}`,前端根据规则显示红色"未匹配到元素"

### Requirement: 候选选择器点击落库为字段

系统 SHALL 接收客户端 `{type:"saveField", pageType:"LIST"|"DETAIL", fieldName:<string>, fieldType:"TEXT"|"NUMBER"|"DATE"|"URL", selector:<string>}` 消息,在该 Page 当前已加载的上下文中(注:本期 PagePreview 仅持 1 个 URL,不区分 list_url / detail_url;`pageType` 由用户在前端选定),调用 M1 现有 `CrawlFieldService.create(configId, request)` 把字段落库到 `crawl_field` 表(`config_id` 来自 WebSocket session 绑定)。系统 SHALL 通过 WebSocket 返回 `{type:"saveFieldResult", ok:<bool>, fieldId:<int?>}`,并在 `ok=false` 时附带 `message` 描述失败原因(配置不存在 / 字段名校重 / 选择器非法)。`configId` 在 WebSocket session 打开时由客户端在 `load` 消息中携带。

#### Scenario: 保存为 LIST 字段
- **WHEN** 客户端在已加载页面的会话中发送 `{type:"saveField", pageType:"LIST", fieldName:"title", fieldType:"TEXT", selector:"div.title"}`,该 config 已存在
- **THEN** 服务端调用 FieldService.create 在 `crawl_field` 插入一条 `config_id=<id>, page_type=LIST, field_name=title, field_type=TEXT, selector="div.title"` 记录,并返回 `{type:"saveFieldResult", ok:true, fieldId:<新插入 id>}`

#### Scenario: 字段名冲突
- **WHEN** 客户端发送的 `fieldName` 已在该 config 下存在相同 page_type 的字段
- **THEN** 服务端返回 `{type:"saveFieldResult", ok:false, message:"字段名已存在"}`,不写入数据库

#### Scenario: 选择器非法
- **WHEN** 客户端发送的 `selector` 字符串在当前 Page 上 evaluate 抛错(语法错误)
- **THEN** 服务端返回 `{type:"saveFieldResult", ok:false, message:"选择器非法"}`,不写入数据库

### Requirement: 端到端用户闭环

系统 SHALL 完整支持以下用户路径:用户在前端输入 URL → 点击"加载" → 等待后端推送 `state=LOADED` 与首帧 `screenshot` → 在截图上点击目标元素 → 等待后端推送 `selectors` 消息 → 选 CSS 或 XPath → 点击"预览匹配" → 等待后端推送高亮截图与 `previewResult` → 填写 fieldName / fieldType / pageType → 点击"保存" → 等待后端推送 `saveFieldResult.ok=true` → 前端显示"已保存"。系统 SHALL 在任一环节失败时,前端 MUST 明确提示并保留已填写内容(不丢失用户输入)。

#### Scenario: 全链路成功
- **WHEN** 用户完整执行上述路径且每一步成功
- **THEN** `crawl_field` 表新增 1 条记录,前端展示绿色"已保存"提示

#### Scenario: 失败后保留输入
- **WHEN** 上述路径中"保存"步骤返回 `ok=false`(例如字段名冲突)
- **THEN** 前端 MUST 仍展示 fieldName / fieldType / pageType / selector 表单当前值,仅显示红色错误提示

### Requirement: 单租户单浏览器会话约束

系统 SHALL 同一时间只允许存在 1 个活跃浏览器会话(单 Page、单 Browser 实例、进程内单例 Bean)。若客户端在已有会话活跃时请求 `POST /api/v1/browser/sessions`,系统 SHALL 拒绝并返回 HTTP 409,`code=409`,`message="已有活跃会话,请先关闭"`。同一会话内允许切换 URL(关闭当前 Page 后在同一 Browser context 中打开新 URL),浏览器进程本身不重启。`DELETE /api/v1/browser/sessions/{id}` 关闭会话后,允许重新打开。

#### Scenario: 重复打开被拒
- **WHEN** 会话已活跃,客户端再次发送 `POST /api/v1/browser/sessions`
- **THEN** 系统返回 HTTP 409,`code=409`,`message="已有活跃会话,请先关闭"`

#### Scenario: 同会话内切换 URL
- **WHEN** 客户端在已打开 https://a.com 的会话中发送 WebSocket `{type:"load", url:"https://b.com"}`
- **THEN** 当前 Page 关闭,在同一 Browser context 中打开 https://b.com,首帧截图推回

#### Scenario: 关闭后允许重开
- **WHEN** 客户端调用 `DELETE /api/v1/browser/sessions/{id}` 成功关闭
- **THEN** 后续 `POST /api/v1/browser/sessions` 允许成功,创建新会话

### Requirement: 1:1 视口与设备像素比策略

系统 SHALL 在创建 Playwright Browser context 时固定 `viewport={width:1280, height:800}`、`deviceScaleFactor=1`,使截图像素与 CSS 像素 1:1 对应。客户端截图展示 MUST 按 1:1 渲染(无 CSS 缩放),用户在截图上点击的视口坐标与发送给后端的 `x` / `y` MUST 直接对应 `document.elementFromPoint` 接受的 CSS 像素坐标,中间不做 DPR 换算。`application.yml` 中 `playwright.viewport.width` / `playwright.viewport.height` / `playwright.viewport.device-scale-factor` 为可配置项,默认 1280 / 800 / 1。

#### Scenario: 默认视口启动
- **WHEN** `application.yml` 未配置 `playwright.viewport.*`
- **THEN** Browser context 启动时 `viewport.width=1280, viewport.height=800, deviceScaleFactor=1`

#### Scenario: 客户端坐标不缩放
- **WHEN** 截图展示 1:1 渲染,用户在截图 (x=300, y=200) 处点击
- **THEN** 前端发送给后端的 `{type:"click", x:300, y:200}` 与后端 `elementFromPoint` 接收的参数一致

### Requirement: iframe 与 shadow DOM 点击不支持

系统 SHALL 在用户点击命中 iframe 内部或 shadow DOM 内部时,`elementFromPoint` 仅返回 iframe 自身或 shadow host;系统 SHALL 不递归进入 iframe / shadow DOM 提取内部元素。`selectors` 消息在这种情况下仍正常返回(返回的是 iframe / shadow host 的选择器),但前端 MUST 在 UI 上提示"该元素位于 iframe / shadow DOM 内,本期不支持深入选择"。

#### Scenario: 命中 iframe
- **WHEN** 用户点击位置命中 `<iframe src="...">` 内部内容
- **THEN** `elementFromPoint` 返回 iframe 元素本身,生成的 CSS / XPath 仅针对 iframe,前端显示提示文案
