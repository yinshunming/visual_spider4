## Why

可视化爬虫的核心交互流是"先看到页面，再选元素"。当前 M1 只完成了项目/字段的 CRUD 闭环，用户还无法把任何真实网页加载进来，导致后续的"可视化选元素 → 生成选择器 → 抽取数据"完全无法启动。本 change 在 MVP 范围内落地最小可用的"输入 URL → 加载页面 → 拿到基础页面信息"闭环，作为后续 WebSocket 截图推送与点击生成选择器的前置依赖。

为符合 MVP 单会话、HTTP 优先、最小可用的约束，本阶段不引入 Playwright 浏览器进程，也不使用 WebSocket 推送截图帧；后端用服务端 HTTP 客户端（基于 java.net.http.HttpClient）拉取目标页面，前端用普通 REST 请求拿到 HTML 字符串和元信息即可。这样：

- 可以在不依赖浏览器进程的前提下打通最小闭环；
- 后续切换到 Playwright + WebSocket 时，业务字段（标题、最终 URL、HTML 片段、状态）保持兼容，只需替换实现层。

## What Changes

### 后端

- 新增 `PageFetchController`：`POST /api/v1/page-fetch` 接收 `{ url }`，返回 `{ status, finalUrl, title, html, contentLength, fetchedAt }`。
- 新增 `PageFetchService`：负责 URL 校验（含基础危险地址拦截）、HTTP GET、HTML 解析（提取 `<title>`、记录最终 URL）、超时控制、错误分类。
- 新增 `PageFetchException` 系列：`InvalidUrlException`（400）、`BlockedAddressException`（403）、`FetchTimeoutException`（504）、`FetchFailedException`（502）。
- 新增 DTO：`PageFetchRequest`、`PageFetchResponse`、`PageFetchStatus` 枚举。
- 在 `GlobalExceptionHandler` 中注册上述异常的响应映射。
- 新增 `WebClientConfig`（仅提供 java.net.http.HttpClient 的 Bean），不引入 WebFlux。

### 前端

- 新增 `/configs/:id/preview` 路由与 `PagePreview.vue`：URL 输入框、"加载"按钮、状态区（loading / success / error）、结果区（标题、最终 URL、字节数、HTML 长度）。
- 新增 `usePageFetchStore`（Pinia）：状态机 `idle → loading → success | error`，暴露 `fetch(request)` action。
- 新增 `api/pageFetch.js`（Axios 封装）。
- 在 `ConfigList.vue` 的"操作"列加一个"预览"按钮，跳到 `/configs/:id/preview`。
- 在 `ConfigEdit.vue` 的页头加一个"打开预览"按钮，行为同上。
- **不**集成可视化选择器、不在页面内嵌 iframe（避免危险地址与跨域问题）。

### 错误反馈语义

| 场景 | HTTP code | 业务 code | message（中文） |
|------|-----------|-----------|-----------------|
| URL 为空 / 格式非法 | 400 | 4001 | "URL 不能为空" / "URL 格式不合法" |
| 命中危险地址（loopback / 私网 / 链路本地 / file / 内网域名后缀） | 403 | 4003 | "目标地址被禁止访问" |
| 目标不可达（DNS 失败、连接拒绝） | 502 | 4002 | "无法访问目标地址：{reason}" |
| 加载超时（默认 8 秒） | 504 | 4004 | "页面加载超时（{n}s）" |
| 响应体超过大小限制（默认 2MB） | 502 | 4005 | "页面内容超过大小限制" |
| 服务端内部异常 | 500 | 5000 | "服务异常" |

### 危险地址限制（MVP，基础即可）

- 协议白名单：只接受 `http` / `https`，其它协议（`file` / `ftp` / `javascript` / `data` 等）直接拒绝。
- 拒绝最常见的回环目标：URL host 等于 `localhost`、或字面 IP 为 `127.0.0.1` / `::1` 时直接拒绝。
- 不做私网段扫描、不做域名后缀黑名单、不做 DNS 解析后再次校验；这些都是"过度防御"，对 MVP 来说不必要。
- 配置项：`page-fetch.allowed-schemes=http,https`、`page-fetch.timeout=8s`、`page-fetch.max-size=2MB`。

## Capabilities

### New Capabilities
- 无（本 change 不新增独立 spec）

### Modified Capabilities
- `page-visual-selection`：在现有"浏览器会话生命周期 / 页面截图实时推送 / 页面加载状态与错误报告 / 点击元素生成选择器"四条 requirement 之外，**新增**一条 `HTTP 同步页面加载（MVP 子集）` requirement，作为本阶段交付的最小闭环；同时将现有的 WebSocket 类 requirement 标记为 `SHALL NOT（本期）`，避免与新增 requirement 冲突。后续迭代再把 WebSocket 能力补齐。

## Impact

### 后端影响

- 新增包与类：
  - `com.visualspider.controller.PageFetchController`
  - `com.visualspider.service.PageFetchService`
  - `com.visualspider.service.UrlGuard`（危险地址校验，纯函数工具类）
  - `com.visualspider.exception.InvalidUrlException`
  - `com.visualspider.exception.BlockedAddressException`
  - `com.visualspider.exception.FetchTimeoutException`
  - `com.visualspider.exception.FetchFailedException`
  - `com.visualspider.dto.request.PageFetchRequest`
  - `com.visualspider.dto.response.PageFetchResponse`
  - `com.visualspider.enums.PageFetchStatus`（`LOADING | SUCCESS | FAILED`）
  - `com.visualspider.config.WebClientConfig`
- 修改 `com.visualspider.exception.GlobalExceptionHandler`：注册上述四个异常。
- 修改 `application.yml`：新增 `page-fetch.*` 配置块。
- 不引入 Playwright，不引入 Spring WebFlux；只用 JDK 自带 `java.net.http.HttpClient`。
- 不影响 M1 现有实体、Repository、Service。

### 前端影响

- 新增 `frontend/src/api/pageFetch.js`
- 新增 `frontend/src/stores/pageFetchStore.js`
- 新增 `frontend/src/views/PagePreview.vue`
- 修改 `frontend/src/router/index.js`：新增 `/configs/:id/preview`
- 修改 `frontend/src/views/ConfigList.vue`：操作列加"预览"按钮
- 修改 `frontend/src/views/ConfigEdit.vue`：页头加"打开预览"按钮
- 不引入新依赖（Vue 3 / Pinia / Element Plus / Axios 已具备）

### 数据库影响

- 无。本 change 不修改 schema。

### 测试影响

- 后端 Service 单测：`PageFetchServiceTest`（Mockito mock `HttpClient`，覆盖成功 / 失败 / 超时 / 大小超限 / 私网地址 / DNS 后 IP 校验）
- 后端 Service 单测：`UrlGuardTest`（覆盖 IPv4 / IPv6 / 域名后缀 / DNS rebinding 场景）
- 后端 Controller 测试：`PageFetchControllerTest`（MockMvc，覆盖四种错误码映射 + 200 成功）
- 前端 Store 测试：`pageFetchStore.test.js`（vitest，覆盖 idle → loading → success / error 状态机）
- 前端组件测试：`PagePreview.test.js`（vitest + @vue/test-utils，覆盖按钮 disabled 联动、错误展示、复制 URL 等关键交互）

### 风险与回滚

- 风险：服务端 HTTP 抓取可能耗时较长，前端超时与后端超时需要分层。
  - 缓解：前端用 `AbortController` 设置 10s 客户端超时（覆盖后端 8s + 网络），后端用 `HttpClient.send(timeout)` 控制实际请求时长。
- 风险：恶意 URL 可能用于 SSRF 攻击内网。
  - 缓解：见"危险地址限制"段，并在 `UrlGuard` 中提供 DNS 解析后再次校验。
- 回滚：删除新增的 controller/service/dto/enum/config，删除前端新增的 view/store/api 与路由即可，不影响 M1 既有功能。
