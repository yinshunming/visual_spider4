# page-visual-selection（页面可视化选择）

## Purpose

通过后端 Playwright 控制的真实浏览器会话，实现页面的可视化加载、实时截图推送、元素点击选择器生成，为用户提供无需手动编写 CSS/XPath 的可视化配置体验。

## Requirements

### Requirement: 浏览器会话生命周期
系统 SHALL 提供单一浏览器会话（同一时间只有一个浏览器上下文），由后端进程直接控制。该会话用于页面导航和选择器生成。

#### Scenario: 打开页面
- **WHEN** 用户提供要打开的 URL
- **THEN** 后端启动或复用 Playwright 浏览器会话，导航到 URL，等待 JS 渲染完成，并通过 WebSocket 将页面截图实时推送到前端

#### Scenario: 复用已有会话
- **WHEN** 用户在已有会话中请求打开新页面
- **THEN** 系统关闭当前页面后在同一会话中打开新 URL；浏览器进程本身不重启

#### Scenario: 关闭浏览器会话
- **WHEN** 用户显式关闭浏览器会话
- **THEN** 系统关闭当前页面；浏览器上下文可保留以供复用

### Requirement: 页面截图实时推送
系统 SHALL 通过 WebSocket 实时向前端推送浏览器视口的截图帧。

#### Scenario: 截图帧传输
- **WHEN** 浏览器页面已加载或发生变化
- **THEN** 系统在 1 秒内向所有已连接的 WebSocket 客户端推送新的截图帧

### Requirement: 页面加载状态与错误报告
系统 SHALL 通过 WebSocket 向前端报告浏览器页面加载状态和任何错误。

#### Scenario: 页面加载成功
- **WHEN** 页面完全加载（包括 JS 渲染）
- **THEN** 系统通过 WebSocket 发送 "LOADED" 状态消息

#### Scenario: 页面加载失败
- **WHEN** 页面加载失败（网络错误、超时或崩溃）
- **THEN** 系统通过 WebSocket 发送包含错误描述的 "ERROR" 状态消息

### Requirement: 点击元素生成选择器
系统 SHALL 允许用户点击浏览器视口中的任意可见元素，系统 SHALL 针对被点击元素生成 CSS 选择器和 XPath 表达式。

#### Scenario: 点击生成选择器
- **WHEN** 用户点击浏览器视口中的某个元素
- **THEN** 系统生成对应的 CSS 选择器和 XPath 表达式并返回给前端

---

### Requirement: HTTP 同步页面加载
系统 SHALL 提供 `POST /api/v1/page-fetch` 同步端点，接收 JSON `{ "url": string }`，返回 `{ status, finalUrl, title, contentLength, fetchedAt }`；请求体中 `url` 为必填，且 MUST 为合法的 http(s) URL。

#### Scenario: 成功加载公开页面
- **WHEN** 客户端发送 `POST /api/v1/page-fetch`，body 为 `{"url":"https://example.com"}`
- **THEN** 系统返回 HTTP 200，`code=200`，`data.status="SUCCESS"`，`data.title` 等于页面 `<title>` 内容，`data.finalUrl` 等于请求 URL（无重定向）或最终重定向 URL，`data.contentLength` 等于响应体字节数，`data.fetchedAt` 为 ISO-8601 时间戳

#### Scenario: 加载带重定向的页面
- **WHEN** 客户端发送 `POST /api/v1/page-fetch`，body 的 URL 会发生 HTTP 重定向
- **THEN** 系统返回 HTTP 200，`data.finalUrl` 等于最终重定向目标的 URL（不是入口 URL）

#### Scenario: URL 为空
- **WHEN** 客户端发送 `POST /api/v1/page-fetch`，body 的 `url` 为空字符串或缺失
- **THEN** 系统返回 HTTP 400，`code=4001`，`message="URL 不能为空"`

#### Scenario: URL 协议非法
- **WHEN** 客户端发送 `POST /api/v1/page-fetch`，body 的 `url` 协议不是 `http` 或 `https`（如 `file://`、`ftp://`、`javascript:`、`data:`）
- **THEN** 系统返回 HTTP 400，`code=4001`，`message="URL 格式不合法"`

#### Scenario: URL 形态非法
- **WHEN** 客户端发送 `POST /api/v1/page-fetch`，body 的 `url` 不是合法的 URI（如 `not-a-url`、`http://`）
- **THEN** 系统返回 HTTP 400，`code=4001`，`message="URL 格式不合法"`

### Requirement: 基础危险地址限制
系统 SHALL 在执行 HTTP 请求前对 URL 做最基础的拦截：协议白名单 + 回环目标拦截。协议只接受 `http` / `https`；URL host 等于 `localhost`（不区分大小写）或字面 IP 为 `127.0.0.1` / `::1` 时 MUST 拒绝。私网段、链路本地、内网域名后缀、DNS 解析后再次校验 IP 不在本期范围。

#### Scenario: 拒绝非 http(s) 协议
- **WHEN** 客户端请求的 URL 协议为 `file` / `ftp` / `javascript` / `data` 之一
- **THEN** 系统返回 HTTP 400，`code=4001`，`message="URL 格式不合法"`

#### Scenario: 拒绝 localhost 主机名
- **WHEN** 客户端请求的 URL host 为 `localhost`（不区分大小写）
- **THEN** 系统返回 HTTP 403，`code=4003`，`message="目标地址被禁止访问"`

#### Scenario: 拒绝 IPv4 回环
- **WHEN** 客户端请求的 URL host 为字面 IP `127.0.0.1`
- **THEN** 系统返回 HTTP 403，`code=4003`，`message="目标地址被禁止访问"`

#### Scenario: 拒绝 IPv6 回环
- **WHEN** 客户端请求的 URL host 为字面 IP `::1`
- **THEN** 系统返回 HTTP 403，`code=4003`，`message="目标地址被禁止访问"`

#### Scenario: 公网域名通过
- **WHEN** 客户端请求的 URL host 是公网域名（如 `https://example.com`）
- **THEN** 系统进入正常 HTTP 请求流程，不返回 403

### Requirement: 加载状态与错误反馈（HTTP 同步版）
系统 SHALL 把抓取过程中遇到的所有错误分类映射到结构化响应：`code` 字段标识错误类别，`message` 字段提供可读中文描述。`status` 字段在成功响应中 MUST 为 `"SUCCESS"`，在抓取失败的业务异常场景下不返回 200 而是用对应 HTTP 状态码。

#### Scenario: 目标不可达
- **WHEN** 客户端请求的 URL 在 DNS 解析、TCP 连接或 HTTP 响应阶段失败（如 NXDOMAIN、Connection Refused、Connection Timeout）
- **THEN** 系统返回 HTTP 502，`code=4002`，`message` 含可读原因（如"无法访问目标地址：xxx"）

#### Scenario: 加载超时
- **WHEN** 客户端请求的 URL 在配置的 `page-fetch.timeout`（默认 8 秒）内未完成响应
- **THEN** 系统返回 HTTP 504，`code=4004`，`message` 含实际超时秒数

#### Scenario: 响应体超过大小限制
- **WHEN** 客户端请求的 URL 响应体字节数超过配置的 `page-fetch.max-size`（默认 2MB）
- **THEN** 系统立即中止读取，返回 HTTP 502，`code=4005`，`message="页面内容超过大小限制"`

#### Scenario: 服务端内部异常
- **WHEN** 服务端在抓取过程中抛出未预期异常
- **THEN** 系统返回 HTTP 500，`code=500`，`message="服务异常"`，详细堆栈不暴露给客户端

### Requirement: 前端 PagePreview 页面
系统 SHALL 在前端 `/configs/:id/preview` 路由提供 `PagePreview.vue` 页面：包含 URL 输入框、"加载"按钮、状态区（loading / success / error）、结果区（标题、最终 URL、字节数、HTML 前 200 字符）。该页面 MUST 走 `usePageFetchStore` 发起请求，组件内不直接调用 Axios。

#### Scenario: 进入预览页
- **WHEN** 用户从 ConfigList 或 ConfigEdit 跳转到 `/configs/:id/preview`
- **THEN** 页面渲染 URL 输入框、"加载"按钮（默认 disabled）、结果区显示占位文案"尚未加载"

#### Scenario: 输入 URL 并点击加载
- **WHEN** 用户在输入框输入合法 http(s) URL 后点击"加载"按钮
- **THEN** 按钮切换为 loading 状态；状态区显示 loading 提示；`usePageFetchStore` 发起请求

#### Scenario: 加载成功
- **WHEN** 后端返回 200 且 `data.status="SUCCESS"`
- **THEN** 按钮恢复；状态区显示绿色"加载成功"；结果区展示 `title` / `finalUrl` / `contentLength` / HTML 前 200 字符

#### Scenario: 加载失败（非 200）
- **WHEN** 后端返回 4xx/5xx 且 `code` 字段为 4001/4002/4003/4004/4005/500 之一
- **THEN** 按钮恢复；状态区显示红色错误提示，内容取自 `message` 字段

#### Scenario: 加载中再次点击按钮
- **WHEN** 当前状态为 loading
- **THEN** 按钮 MUST 处于 disabled / loading 状态，不可重复触发请求

#### Scenario: 页面不内嵌 iframe
- **WHEN** 预览页展示结果
- **THEN** 页面 MUST NOT 渲染目标页面 iframe；仅展示元信息（title / finalUrl / contentLength / HTML 片段）

### Requirement: ConfigList 与 ConfigEdit 跳转入口
系统 SHALL 在 ConfigList 的"操作"列与 ConfigEdit 的页头提供"预览"按钮，点击后跳转至 `/configs/:id/preview`，不要求后端额外接口。

#### Scenario: ConfigList 跳转预览
- **WHEN** 用户在 ConfigList 表格行点击"预览"按钮
- **THEN** 路由跳转至 `/configs/:id/preview`

#### Scenario: ConfigEdit 跳转预览
- **WHEN** 用户在 ConfigEdit 页头点击"打开预览"按钮
- **THEN** 路由跳转至 `/configs/:id/preview`，原编辑页状态保留（用户可返回继续编辑）

---

### Requirement: 点击元素生成 CSS 与 XPath 候选

系统 SHALL 在 WebSocket `/api/v1/ws/page` 通道接收客户端 `{type:"click", x:<int>, y:<int>}` 消息后,在当前 Playwright Page 上调用 `page.evaluate("document.elementFromPoint(x, y)")` 拿到目标元素,同时为该元素生成 CSS 与 XPath 两种候选选择器,并通过同一通道返回 `{type:"selectors", css:{selector,matchCount,samples}, xpath:{selector,matchCount,samples}}`。`matchCount` 是在当前已加载页面 DOM 上执行该选择器得到的元素数量;`samples` 是前 5 个匹配元素的 `textContent.trim()`(若元素无文本则取 `outerHTML` 前 80 字符)。CSS 候选 MUST 由后端自写 `CssSelectorGenerator` 生成（id 优先 / class 追加 / :nth-of-type(n) 谓词），XPath 候选 MUST 由后端自写 `XPathGenerator` 生成（沿 ancestor chain 拼轴与谓词）。若 `elementFromPoint` 返回 null(点击位置没有命中任何元素),系统 SHALL 返回 `{type:"error", code:"NO_ELEMENT", message:"未命中任何元素"}`。

#### Scenario: 点击命中普通 div
- **WHEN** 客户端发送 `{type:"click", x:120, y:80}`,该坐标命中一个 `<div class="title">示例</div>`
- **THEN** 服务端返回 `{type:"selectors", css:{selector:"div.title", matchCount:>=1, samples:["示例"]}, xpath:{selector:"//div[@class='title']" 或等价轴+谓词, matchCount:>=1, samples:["示例"]}}`

#### Scenario: 点击未命中元素
- **WHEN** 客户端发送 `{type:"click", x:-10, y:-10}`(坐标越界)
- **THEN** 服务端返回 `{type:"error", code:"NO_ELEMENT", message:"未命中任何元素"}`

#### Scenario: CSS 与 XPath 候选同时返回
- **WHEN** 服务端返回 selectors 消息
- **THEN** `css` 与 `xpath` 两个字段 MUST 都不为 null

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
