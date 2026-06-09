# page-visual-selection（页面可视化选择）

## Purpose

通过后端 Playwright 控制的真实浏览器会话，实现页面的可视化加载、实时截图推送、元素点击选择器生成，为用户提供无需手动编写 CSS/XPath 的可视化配置体验。

## ADDED Requirements

### Requirement: 浏览器会话生命周期
系统 SHALL 提供单一浏览器会话（同一时间只有一个浏览器上下文），由后端进程直接控制。该会话用于页面导航和选择器生成。

#### 场景：打开页面
- **WHEN** 用户提供要打开的 URL
- **THEN** 后端启动或复用 Playwright 浏览器会话，导航到 URL，等待 JS 渲染完成，并通过 WebSocket 将页面截图实时推送到前端

#### 场景：复用已有会话
- **WHEN** 用户在已有会话中请求打开新页面
- **THEN** 系统关闭当前页面后在同一会话中打开新 URL；浏览器进程本身不重启

#### 场景：关闭浏览器会话
- **WHEN** 用户显式关闭浏览器会话
- **THEN** 系统关闭当前页面；浏览器上下文可保留以供复用

### Requirement: 页面截图实时推送
系统 SHALL 通过 WebSocket 实时向前端推送浏览器视口的截图帧。

#### 场景：截图帧传输
- **WHEN** 浏览器页面已加载或发生变化
- **THEN** 系统在 1 秒内向所有已连接的 WebSocket 客户端推送新的截图帧

### Requirement: 页面加载状态与错误报告
系统 SHALL 通过 WebSocket 向前端报告浏览器页面加载状态和任何错误。

#### 场景：页面加载成功
- **WHEN** 页面完全加载（包括 JS 渲染）
- **THEN** 系统通过 WebSocket 发送 "LOADED" 状态消息

#### 场景：页面加载失败
- **WHEN** 页面加载失败（网络错误、超时或崩溃）
- **THEN** 系统通过 WebSocket 发送包含错误描述的 "ERROR" 状态消息

### Requirement: 点击元素生成选择器
系统 SHALL 允许用户点击浏览器视口中的任意可见元素，系统 SHALL 针对被点击元素生成 CSS 选择器和 XPath 表达式。

#### 场景：点击生成选择器
- **WHEN** 用户点击浏览器视口中的某个元素
- **THEN** 系统生成对应的 CSS 选择器和 XPath 表达式并返回给前端

---

## M2 同步页面加载 MVP（M2 sync page loading MVP）

> 下列 Requirements 描述的是"在引入 Playwright 之前"的 MVP 切片，与上方 Playwright + WebSocket 类 requirement **并存**：同步 HTTP 抓取用于快速预览页面元信息（title / finalUrl / contentLength），后续 Playwright 阶段用于可视化选择器生成。

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
