## Context

M1 已经把项目/字段的 CRUD 闭环跑通：用户能创建配置、维护字段、看列表/编辑页。但可视化爬虫的核心交互"看到目标页面"还没有任何能力。`page-visual-selection` spec 里描述的"Playwright 浏览器会话 + WebSocket 截图推送"对 MVP 来说太重：需要引入 Playwright 运行时、浏览器二进制、新的 WebSocket 通道、客户端 iframe/screenshot 渲染链路，scope 膨胀快。

本 change 退一步：在 MVP 范围内用**服务端 HTTP 抓取 + 同步 REST** 实现"输入 URL → 拿到 HTML 字符串 + 标题 + 最终 URL + 状态"这一最小闭环。这样：

- 不引入 Playwright、不引入 WebSocket；
- 业务字段（title、finalUrl、status、html 长度）和后续 WebSocket 阶段保持兼容；
- 能在不依赖任何外部进程的前提下完成端到端验证。

约束来自既有 spec 与 AGENTS.md：

- 后端包名 `com.visualspider`；controller/service/repository/entity/dto/exception 既有分层。
- 统一响应 `ApiResponse<T>{code, data, message}`。
- 字段枚举与服务端校验保持一致风格（record + Bean Validation）。
- TDD：所有可测试行为走 RED→GREEN→REFACTOR；垂直切片。
- 单会话、单用户（来自 system-boundaries）：本期只暴露"按 URL 单次抓取"接口，不维护会话状态。
- 危险地址限制（本期新增）：在 service 入口做协议/域名/IP 三层校验，并在 DNS 解析后再次校验 IP 防 DNS rebinding。

## Goals / Non-Goals

**Goals:**
- 暴露一个同步 REST 端点 `POST /api/v1/page-fetch`，输入 URL、输出页面元信息与 HTML 片段。
- 前端在 `/configs/:id/preview` 页面提供 URL 输入、加载按钮、状态展示、结果展示。
- 错误分层：URL 非法 / 危险地址 / 不可达 / 超时 / 响应过大。
- 默认开启"私网/回环地址"拦截（可配置关闭，仅限开发环境）。
- TDD：后端 service / controller / util 全部走测试先行；前端 store 走 vitest。
- 不破坏 M1 既有功能；所有现有 37 个测试保持绿。

**Non-Goals:**
- 不引入 Playwright；不启动浏览器进程。
- 不维护浏览器会话、不复用页面。
- 不做 WebSocket 截图推送（保留给后续 change）。
- 不在页面内嵌入 iframe（避免跨域与 XSS）。
- 不做选择器生成、不做点击拾取。
- 不存 HTML、不存截图（本期不落库）。
- 不支持重定向后的最终 cookie / 鉴权跟随。
- 不实现"增量加载"或"分块读取"。

## Decisions

### 1. 使用 JDK 自带 `java.net.http.HttpClient`，不引入 WebClient / OkHttp

**决策**：后端用 JDK 21 自带的 `java.net.http.HttpClient`，注册为 Spring Bean，service 直接注入。

**理由**：
- 项目无任何其它 HTTP 抓取需求；M1 阶段只用一次。
- 零额外依赖，减少 jar 体积与版本冲突面。
- 同步 API + `BodyHandlers.ofInputStream()` 配合 `HttpRequest.Builder.timeout()` 可以同时控制"读 body 的总时间"和"建立连接的超时"。
- 后续如需更高性能或可观察性，再切到 Reactor Netty / Apache HttpClient 5，影响面只限 `WebClientConfig` + `PageFetchService`。

**替代方案**：
- Spring `RestClient`（6.1+）：与 WebFlux 解耦，API 友好，但本质仍是同步；与 JDK HttpClient 等价。保留作为"如果需要拦截器/重试"的备选。
- Apache HttpClient 5 / OkHttp：功能强，但要拉依赖。本期不需要。

### 2. URL 校验（极简版）：协议白名单 + 最基础回环拦截

**决策**：`UrlGuard` 工具类只做两件事：
1. 协议白名单：`http` / `https`；其它（`file` / `ftp` / `javascript` / `data` / 自定义）直接拒绝。
2. 最基础回环拦截：URL host 等于 `localhost`（不区分大小写）或字面 IP 为 `127.0.0.1` / `::1` 时直接拒绝。

**理由**：
- MVP 阶段主要是防止"误把自己或同事的内网服务当目标"——这两个最常见。
- 私网段、链路本地、内网域名后缀、DNS rebinding 都属于"过度防御"。本期不引入，理由是：真实业务里用户输入的 URL 大概率是公网域名，过度校验对正常使用没有收益，反而带来配置/测试负担。
- 留出扩展点：未来要做更严格的 SSRF 防护，单独开 change 在 `UrlGuard` 里加 IP 段检查即可，接口与调用点不动。

**替代方案**：
- 三层防线（协议 + 形态 + DNS 后 IP 校验）：复杂度高、DNS rebinding 在 JDK 层只能用"先解析后发请求"的折中方案，对 MVP 投入产出比不划算，否决。
- 完全不拦截：service 直接调 `HttpClient.send`，最简，但用户一旦误填 `http://127.0.0.1:8080/api/v1/health` 会导致后端自我探测，保留最基础拦截是必要的。

### 3. 错误用专用异常 + 业务 code，由 GlobalExceptionHandler 统一翻译

**决策**：
- 自定义异常 `InvalidUrlException` / `BlockedAddressException` / `FetchTimeoutException` / `FetchFailedException` / `ResponseTooLargeException`。
- 继承 `BusinessException`，带 `code` 字段。
- 在 `GlobalExceptionHandler` 中按异常类型映射到 `ApiResponse<Void>`，HTTP 状态码与 `code` 字段值见 proposal 表格。

**理由**：
- 与 M1 既有 `ConfigNotFoundException` 一致，保持项目风格统一。
- 业务 code 与 HTTP code 解耦：前端拿到 HTTP 200，但 `code` 是 4001/4003/4004 等，前端用 `code` 做差异化提示。
- 错误信息本地化（中文字符串）写在异常 message 里，避免 i18n 复杂度（system-boundaries 已声明单用户、单语言）。

**替代方案**：
- 用 `ResponseStatusException` + 枚举：缺少业务 code 字段；与既有 `BusinessException` 体系不一致。
- 全部塞进 controller 用 `try-catch`：service 可测试性下降。

### 4. 同步阻塞调用，async 留给后续

**决策**：`PageFetchController` 是同步 `@RestController`；`PageFetchService` 用 `HttpClient.send` 阻塞。Spring MVC 默认 Tomcat 线程池可承受偶尔的长请求（8s 内）。

**理由**：
- MVP 阶段低频操作，不需要 `CompletableFuture` / `WebFlux`。
- 前端单按钮触发，UX 上加 loading 状态即可。
- 测试简单：service 测同步返回值；controller 测 MockMvc。

**替代方案**：
- `@Async` 异步 + 前端轮询：复杂度高、收益低。
- 推送 SSE / WebSocket：留到下个 change。

### 5. 大小限制用 `InputStream` + 计数器，不用 `Content-Length` 头

**决策**：用 `HttpResponse.BodyHandlers.ofInputStream()`，service 拿到 `InputStream` 后用 `transferTo` 写到 `ByteArrayOutputStream`，写入字节数 > `max-size` 立即抛 `ResponseTooLargeException` 并关闭连接。

**理由**：
- 不信任 `Content-Length`：服务器可能撒谎（恶意或不规范）。
- 边读边计数：提前中止能避免被几十 GB 响应拖死内存。
- 默认 `max-size=2MB` 写进 `application.yml`，可配置。

**替代方案**：
- `BodyHandlers.ofByteArray()`：直接吃满内存才校验，2MB 上限场景风险大。
- `BodyHandlers.ofLines()`：对 HTML 不友好。

### 6. 状态机：前端 store `idle → loading → success | error`

**决策**：`usePageFetchStore` 暴露 `status: 'idle' | 'loading' | 'success' | 'error'`，`lastResult: PageFetchResponse | null`，`lastError: string | null`。`fetch(request)` action 内部 try/catch，错误统一写 `lastError` 并切到 `error`。

**理由**：
- 与 M1 既有 store 风格保持一致（参考 `useConfigStore`）。
- View 层 `v-if="status==='loading'"` 切换 loading / 错误 / 成功区，无须内部重复判断。
- 单请求状态机，没有"并发请求"问题；如有新需求再升级。

**替代方案**：
- 不用 Pinia 直接在组件内 `ref`：跨组件复用难（如 ConfigList 跳转过来时希望恢复上次结果）。
- 用 `useFetch` 组合式：项目其它 store 都是 Pinia，统一风格更好。

### 7. 不内嵌 iframe，纯元信息展示

**决策**：`PagePreview.vue` 只展示 `title` / `finalUrl` / `contentLength` / `html` 前 N 个字符（最多 200 字符截断），**不**渲染 iframe。

**理由**：
- 用户可能输入恶意页面，渲染 iframe 等于把 XSS 风险转嫁给前端。
- 后续切到截图推送时，前端自然就不需要 iframe 了。
- 用户要看页面原貌，直接在另一个 tab 打开 `finalUrl` 更安全。

**替代方案**：
- 内嵌 iframe + sandbox：仍要解决跨域；属于下一个 change 范围。

### 8. TDD 切片粒度

**决策**：每个切片是"一个测试 + 一个最小实现"，从工具类（`UrlGuard`）开始，再到 service，再到 controller，最后前端。

**理由**：
- `UrlGuard` 是纯函数，单测最便宜，先打通。
- `PageFetchService` 是有外部依赖（HttpClient）的有状态计算，用 Mockito mock。
- `PageFetchController` 走 `@WebMvcTest` + MockMvc 验证 HTTP 翻译。
- 前端 store 走 vitest；view 走 `@vue/test-utils` 的渲染测试。

详细切片见 `tasks.md`。

## Risks / Trade-offs

- [SSRF 风险] → 协议白名单 + 最基础回环拦截（`localhost` / `127.0.0.1` / `::1`）。私网段、域名后缀、DNS rebinding 不做，留待后续 change 按需扩展。
- [大响应内存压力] → `ofInputStream` + 字节计数器 + 2MB 上限；超限立即关流抛异常。
- [抓取慢影响 Tomcat 线程] → 默认超时 8s，客户端 `AbortController` 设 10s 兜底；单 endpoint 不会被滥用（前端只有一个按钮）。
- [DNS 解析本身超时] → JDK HttpClient 的 `resolveTimeout` 默认 30s，本期用全局超时 8s 兜住，必要时再细化。
- [TDD 初学成本] → tasks 全部按 RED→GREEN→REFACTOR 标注；复杂切片先写失败测试再写最小实现。
- [后续切换到 Playwright 的迁移成本] → 业务字段（`title` / `finalUrl` / `html` / `status`）保持不变；只需替换 `PageFetchService` 的实现 + 加 WebSocket 通道 + 加 iframe 渲染。**接口与 DTO 都不动**，前端 store 也不动。
- [中文字符串硬编码] → 暂不引入 i18n；与 system-boundaries 的"单用户、单语言"一致。如果未来要做双语，把 message 抽到 messages.properties 即可。
- [没有 WebSocket 推送，加载中用户体验略平] → 接受。8s 内的同步等待用 loading spinner 已经够用。

## Migration Plan

本期是纯增量，没有 schema 变更、没有 API 删除；部署按以下顺序：

1. 合并后端 PR → 自动跑 `mvn test` 验证（含新增测试）。
2. 合并前端 PR → 跑 `npm test` 与 `npm run build`。
3. 启动顺序：PG → 后端 → 前端；`/api/v1/health` 返回 UP。
4. 端到端手工冒烟：
   - 输入 `https://example.com` → 看到 200 + title "Example Domain" + 最终 URL 等于请求 URL。
   - 输入 `http://127.0.0.1:8080/api/v1/health` → 看到 403 + 业务 code 4003。
   - 输入 `not-a-url` → 看到 400 + 业务 code 4001。
   - 输入 `https://httpbin.org/delay/30` → 看到 504 + 业务 code 4004（>8s）。
5. 回滚：删除新增的 controller/service/util/dto/enum/config 与前端新增的 view/store/api/route；不影响 M1 既有功能。

## Open Questions

- 加载中是否要给前端"取消"按钮？本期暂不实现（`<el-button :loading="...">` 的 loading 状态就够用），下个 change 再补 cancel API。
- HTML 是否要返回原文给前端？目前计划只返回前 200 字符截断用于展示，"完整 HTML"留给后续截图/选择器阶段。
- 是否需要记录"最近一次抓取时间戳"到 CrawlConfig？本期不动 schema。
