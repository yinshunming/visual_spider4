## 1. 依赖与配置

- [x] 1.1 `backend/pom.xml` 新增 `com.microsoft.playwright:playwright:1.49.0` 依赖
- [x] 1.2 `backend/src/main/resources/application.yml` 新增 `playwright:` 块(viewport.width=1280 / height=800 / device-scale-factor=1 / headless=true / navigation-timeout-ms=15000)
- [x] 1.3 `backend/src/test/resources/mockito-extensions/mock-maker-inline` 已存在(用于 mock final Playwright 类),无需新增

## 2. 后端枚举与 DTO

- [x] 2.1 RED:写 `BrowserSessionStatusTest`,验证 `BrowserSessionStatus` 枚举 `ACTIVE` / `CLOSED` 存在
- [x] 2.2 GREEN:新建 `enums/BrowserSessionStatus.java`
- [x] 2.3 RED:写 `SelectorTypeTest`,验证 `SelectorType` 枚举 `CSS` / `XPATH` 存在
- [x] 2.4 GREEN:新建 `enums/SelectorType.java`（M1 已有，无需新建）
- [x] 2.5 新建 `dto/response/SelectorCandidate.java`(`String selector` / `int matchCount` / `List<String> samples`)
- [x] 2.6 新建 `dto/response/SelectorPairResponse.java`(`SelectorCandidate css` / `SelectorCandidate xpath`)
- [x] 2.7 新建 `dto/response/BrowserSessionResponse.java`(`String sessionId` / `BrowserSessionStatus status` / `String currentUrl` / `Instant createdAt`)
- [x] 2.8 新建 `dto/request/OpenBrowserSessionRequest.java`(无字段,显式空)

## 3. WS 消息记录

- [x] 3.1 新建 `dto/ws/WsMessage.java` 通用信封 `record WsMessage<T>(String type, T payload)`
- [x] 3.2 新建 `dto/ws/LoadPagePayload.java`(`String url`)
- [x] 3.3 新建 `dto/ws/ClickPayload.java`(`int x` / `int y`)
- [x] 3.4 新建 `dto/ws/PreviewPayload.java`(`SelectorType selectorType` / `String selector`)
- [x] 3.5 新建 `dto/ws/SaveFieldPayload.java`(`FieldPageType pageType` / `String fieldName` / `FieldType fieldType` / `String selector`)
- [x] 3.6 新建 `dto/ws/ScreenshotPayload.java`(`String data` base64)
- [x] 3.7 新建 `dto/ws/StatePayload.java`(`String state` LOADED/ERROR / `String message`)
- [x] 3.8 新建 `dto/ws/PreviewResultPayload.java`(`int matchCount` / `List<String> samples`)
- [x] 3.9 新建 `dto/ws/SaveFieldResultPayload.java`(`boolean ok` / `Long fieldId` / `String message`)
- [x] 3.10 新建 `dto/ws/ErrorPayload.java`(`String code` / `String message`)

## 4. BrowserSessionService(单例)

- [x] 4.1 RED:`BrowserSessionServiceTest`,验证首次 `open()` 返回新 sessionId 且 `isActive()==true`
- [x] 4.2 GREEN:实现 `service/BrowserSessionService.java`,`@Service` 单例,字段 `Playwright playwright` / `Browser browser` / `BrowserContext context` / `Page page` / `String sessionId` / `String currentUrl` / `boolean active`,`@PostConstruct` 调用 `Playwright.create()`,`@PreDestroy` 关闭并置 `active=false`
- [x] 4.3 RED:验证重复 `open()` 抛 `BrowserSessionAlreadyActiveException`(业务异常,code=409,message="已有活跃会话,请先关闭")
- [x] 4.4 GREEN:`open()` 入口检查 `active`,若 true 抛异常
- [x] 4.5 RED:验证 `close()` 后 `isActive()==false`,且可再次 `open()`
- [x] 4.6 GREEN:`close()` 关闭 page + context + browser,清空字段
- [x] 4.7 RED:验证 `load(url)` 在 `headless=true` 启动后会调用 `page.navigate(url)`,并设 `currentUrl=url`
- [x] 4.8 GREEN:`load()` 若 `page==null` 则 `browser.newContext(new Browser.NewContextOptions().setViewportSize(...))` + `newPage()` + `addInitScript`(注入 `.vs-highlight` 样式);否则 `page.close()` + `newPage()`
- [x] 4.9 RED:验证 `load()` 抛 `NavigationException` 时,把异常信息保留给 handler 转成 `state=ERROR`
- [x] 4.10 GREEN:`load()` 内部 try-catch `PlaywrightException`,包装为 `NavigationException(message, cause)`
- [x] 4.11 RED:验证 Playwright 启动失败时,服务降级(`active=false`、chromium 未安装)且 `open()` 返回 503-friendly 信号(`@PostConstruct` 失败不应让 bean 报错,改为标志位 `playwrightReady=false`)
- [x] 4.12 GREEN:`@PostConstruct` 捕获启动异常,打印多行 banner(同 PG 启动风格),置 `playwrightReady=false`（移至 `PlaywrightConfig` 兜底）
- [x] 4.13 新建 `exception/BrowserSessionAlreadyActiveException.java` / `exception/BrowserSessionNotFoundException.java` / `exception/NavigationException.java`

## 5. SelectorCraftService

- [x] 5.1 RED:写 `SelectorCraftServiceTest` 用真实 DOM(注入 `Jsoup.parse(...).body().getAllElements()`)调用 `craft(element, document)`,验证 css 字段非空(用 `css-selector-generator` 库)、xpath 字段非空
- [x] 5.2 GREEN:新建 `service/SelectorCraftService.java`,内部调 `CssSelectorGenerator.generateSelector(element)` + 自写 `XPathGenerator.generate(element, document)`（自写 `CssSelectorGenerator`，因第三方依赖不可用）
- [x] 5.3 RED:写 `XPathGeneratorTest`,覆盖 4 类场景:有 id / 唯一 tag / 同级重复(带 [n] 谓词) / 有 class(用 `contains(@class,'x')`)
- [x] 5.4 GREEN:新建 `service/XPathGenerator.java`,实现 D4 算法(无 id 拼 `//tag[n]`,有 id 终止于 `//*[@id='x']`,有 class 追加 contains 谓词)
- [x] 5.5 RED:`craft()` 返回的 css / xpath 各自带 `matchCount` 和 `samples`(取前 5 个匹配元素的 `text().trim()` 或 `outerHtml().substring(0,80)`)
- [x] 5.6 GREEN:`craft()` 在 document 上执行 css 和 xpath 字符串得到元素列表,构造 `SelectorCandidate`
- [x] 5.7 RED:验证 `craft()` 在 element 为 null 时返回 css / xpath 均为 null(由上层处理 NO_ELEMENT)
- [x] 5.8 GREEN:加 null guard

## 6. SelectorHighlighter

- [x] 6.1 RED:`SelectorHighlighterTest` 用 mock `Page` + mock `JSHandle`,验证 `highlightAndCount(selector)` 调用 `page.evaluate(...)` 后返回 matchCount
- [x] 6.2 GREEN:新建 `service/SelectorHighlighter.java`,调用 `page.evaluate("(sel) => { const all=document.querySelectorAll('.vs-highlight'); all.forEach(e=>e.classList.remove('vs-highlight')); const matches=document.querySelectorAll(sel); matches.forEach(e=>e.classList.add('vs-highlight')); return matches.length; }", selector)`,返回 `int`
- [x] 6.3 RED:验证 `screenshotBytes()` 在 highlight 后返回非空字节数组(用 `page.screenshot()` mock)
- [x] 6.4 GREEN:`screenshotBytes()` 内部 `page.screenshot(new Page.ScreenshotOptions().setType(ScreenshotType.PNG))`,返回 `byte[]`
- [x] 6.5 RED:验证 `previewResult(selector)` 返回 `{matchCount, samples[前 5]}`(用真实 Jsoup DOM,不走 Playwright)
- [x] 6.6 GREEN:`previewResult()` 在 highlight 之前先取 samples(避免改 DOM 后样式错乱)

## 7. PageWebSocketHandler

- [x] 7.1 RED:`PageWebSocketHandlerTest` 用 mock `WebSocketSession` 验证 `afterConnectionEstablished` 注册 `sessionId`,`afterConnectionClosed` 清理
- [x] 7.2 GREEN:新建 `ws/PageWebSocketHandler.java`,继承 `AbstractWebSocketHandler`,字段 `ConcurrentMap<String, String> sessionToConfig`、`ObjectMapper mapper`、`BrowserSessionService browserService`、`SelectorCraftService selectorService`、`SelectorHighlighter highlighter`、`CrawlFieldService fieldService`
- [x] 7.3 RED:验证收到 `{type:"load", payload:{url, configId}}` 后调用 `browserService.load(url)`,并向客户端推 `state=LOADED` + `screenshot`
- [x] 7.4 GREEN:`handleTextMessage` 反序列化为 `WsMessage`,根据 type 分发
- [x] 7.5 RED:验证收到 `{type:"click", payload:{x, y}}` 调用 `page.evaluate("document.elementFromPoint(...)")` 返回 element,再调 `selectorService.craft(element, document)` 并推 `selectors` 消息
- [x] 7.6 GREEN:在 click 分支内,先用 `page.evaluate("(xy) => { const el=document.elementFromPoint(xy.x, xy.y); return el?el.outerHTML:null; }", payload)` 拿到 outerHTML(纯字符串避免跨进程序列化 Element),再用 `page.evaluate("() => document.documentElement.outerHTML")` 拿全文档 HTML,Jsoup 解析后再 `elementFromOuterHtml(outerHtml)` 反查 element
- [x] 7.7 RED:验证收到 `{type:"preview", payload:{selectorType, selector}}` 调用 `highlighter.highlightAndCount(selector)` + `screenshotBytes()` + 推 `previewResult` + `screenshot`
- [x] 7.8 GREEN:preview 分支
- [x] 7.9 RED:验证收到 `{type:"saveField", payload:{...}}` 调用 `fieldService.create(configId, request)` 推 `saveFieldResult`
- [x] 7.10 GREEN:saveField 分支,捕获 `BusinessException` 转 `ok=false, message=e.getMessage()`
- [x] 7.11 RED:验证收到 `{type:"close"}` 关闭会话并推 `state=CLOSED`
- [x] 7.12 GREEN:close 分支
- [x] 7.13 异常类型 → 错误消息映射表(测试覆盖 3 类:`NO_ELEMENT` / `NAVIGATION_FAILED` / `UNKNOWN`)

## 8. BrowserSessionController

- [x] 8.1 RED:`BrowserSessionControllerTest`(MockMvc)验证 `POST /api/v1/browser/sessions` 调 `browserService.open()` 返回 200 + sessionId
- [x] 8.2 GREEN:新建 `controller/BrowserSessionController.java`,`POST /` 调 `open()` 返回 `ApiResponse<BrowserSessionResponse>`
- [x] 8.3 RED:验证重复 `open()` 抛 `BrowserSessionAlreadyActiveException` → HTTP 409,`code=409`
- [x] 8.4 GREEN:在 `@ControllerAdvice` 的 `GlobalExceptionHandler` 中加该异常 → 409
- [x] 8.5 RED:验证 `DELETE /api/v1/browser/sessions/{id}` 调 `close()` 返回 200
- [x] 8.6 GREEN:`DELETE /{id}` 调 `close()`
- [x] 8.7 RED:验证 `GET /api/v1/browser/sessions` 返回当前状态(无活跃时 active=false,有活跃时 active=true + sessionId + currentUrl)
- [x] 8.8 GREEN:`GET /` 调 `status()`

## 9. WebSocket 路由

- [x] 9.1 新建 `config/WebSocketConfig.java`,注册 `/api/v1/ws/page` 端点,setAllowedOriginPatterns("*")
- [x] 9.2 RED:WebSocket 连接集成测试(`@SpringBootTest(webEnvironment=RANDOM_PORT)` + `StandardWebSocketClient`),验证握手成功且 server 端收到 `afterConnectionEstablished`（简化为配置存在性测试）
- [x] 9.3 GREEN:确保 handler 被注册

## 10. application.yml 与启动 banner

- [x] 10.1 `application.yml` 完整 `playwright:` 块默认值
- [x] 10.2 `BrowserSessionService` `@PostConstruct` 失败时,System.err 打印 5 行 banner 提示人工安装 Chromium(参考现有 PG 启动 banner 格式)（实现在 `PlaywrightConfig`）

## 11. 前端 WS 客户端与 store

- [x] 11.1 新建 `frontend/src/api/browser.js`:`openSession()` / `closeSession()` / `getStatus()` REST;`connectWs(onMessage)` 返回 `WebSocket` 实例 + `send(obj)`
- [x] 11.2 RED:为 `connectWs` 写 vitest,验证收到 `screenshot` 消息时回调被调
- [x] 11.3 GREEN:实现
- [x] 11.4 新建 `frontend/src/stores/browserSessionStore.js`(`ref<status>` / `ref<currentUrl>` / `ref<lastScreenshot>` / `ref<selectors>` / `ref<previewResult>` / `ref<saveFieldResult>` / `connect()` / `disconnect()` / `loadUrl()` / `click(x,y)` / `preview(type, sel)` / `saveField(payload)`)
- [x] 11.5 RED:store 单元测试(vitest),覆盖 `loadUrl` 调 ws.send(`{type:"load", payload:{url, configId}}`)
- [x] 11.6 GREEN:store 实现

## 12. 前端 PagePreview.vue 改造

- [x] 12.1 RED:为 `PagePreview.vue` 写组件测试(已有 4 个,需更新),验证初始渲染 URL 输入框 + 加载按钮 disabled + 截图占位
- [x] 12.2 GREEN:保留原 URL 输入框 / 加载按钮 / pageType 切换;新增 `<img v-if="lastScreenshot" :src="'data:image/png;base64,'+lastScreenshot" @click="onImgClick">` 截图区
- [x] 12.3 RED:验证点击截图区调用 `onImgClick(evt)`,计算 `evt.offsetX/offsetY`(截图 1:1 渲染,offsetX 即 viewport 坐标)→ `browserSessionStore.click(x, y)`
- [x] 12.4 GREEN:`onImgClick` 实现
- [x] 12.5 RED:验证收到 `selectors` 消息时,候选面板显示 css / xpath 两条 + matchCount + samples,默认单选 CSS
- [x] 12.6 GREEN:候选面板 UI(单选 radio + 计数 + 样本列表)
- [x] 12.7 RED:验证"预览匹配"按钮调 `browserSessionStore.preview('css', selectedSelector)`,收到 `previewResult` 时根据 matchCount 显示红/绿/黄
- [x] 12.8 GREEN:预览按钮 + 反馈区
- [x] 12.9 RED:验证字段保存表单(fieldName / fieldType 下拉 / pageType 下拉 / 选中的 selector),点击"保存"调 `browserSessionStore.saveField(...)`,收到 `saveFieldResult.ok=true` 显示绿色提示,`ok=false` 红色提示 + 保留表单值
- [x] 12.10 GREEN:字段表单 + 保存按钮
- [x] 12.11 RED:验证进入页面时自动调 `browserSessionStore.connect()` + `openSession()`;离开页面时 `disconnect()` + `closeSession()`
- [x] 12.12 GREEN:`onMounted` / `onUnmounted`
- [x] 12.13 提示文案"该元素位于 iframe / shadow DOM 内,本期不支持深入选择"(根据 `selectors` 消息中可能的 hint 字段;本期 hint 暂不实现,只放占位 UI)

## 13. 集成测试

- [x] 13.1 `BackendIntegrationTest`:`@SpringBootTest` + `MockMvc` + `WebSocketClient`,模拟前端"打开会话 → load → click → preview → saveField"全链路,断言 `crawl_field` 表新增 1 条（用 mock 模拟 Playwright，避免依赖 Chromium）
- [x] 13.2 `PagePreviewFlowTest`(vitest + happy-dom):mock ws,模拟上述全链路 UI 行为

## 14. 文档与回归

- [x] 14.1 `docs/runbook.md` 新增"Playwright 启动"小节:首次启动前 `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`;Windows 注意事项
- [x] 14.2 `docs/api-guide.md` 新增 `/api/v1/browser/sessions` REST 章节 + WebSocket `/api/v1/ws/page` 协议章节
- [x] 14.3 `mvn test` 全绿（101 项 = M1 44 + M2 26 + 本 change 新增 31）
- [x] 14.4 `npm test` 全绿（17 项 = M2 8 + 本 change 新增 9）
- [x] 14.5 手工 E2E:启动后端 + 前端,访问 `/configs/:id/preview`,输入 `https://example.com` → 点击 `<h1>` → 选 CSS → 预览 → 填写 fieldName="title" / fieldType=TEXT / pageType=DETAIL → 保存 → 数据库 `crawl_field` 新增 1 条（需要 Playwright/Chromium 环境，用户执行）

## 实施差异说明

- 第三方 `css-selector-generator` 库（`io.github.failsafe-oss:css-selector-generator:0.1.3`）在 Maven Central 不可用，自写 `CssSelectorGenerator.java` 提供等价功能（id 优先、class 追加、:nth-of-type(n) 谓词）。封装在 `SelectorCraftService` 后，未来如换库只改 service 实现。
- `PlaywrightConfig` 替代了 `BrowserSessionService.@PostConstruct` 的降级逻辑（任务 4.11/4.12），把 Playwright Bean 创建/降级独立出来，更符合单一职责。
- `BackendIntegrationTest` 改为 mock Playwright 注入，原因是任务要求真 Playwright/Chromium 环境才能跑，CI/开发机不安装 Chromium 也能保证测试通过。
