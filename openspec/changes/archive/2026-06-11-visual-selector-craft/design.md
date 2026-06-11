## Context

M2 已落地"无头 HTTP 抓取元信息"(`POST /api/v1/page-fetch` 同步返回 title / finalUrl / contentLength)与 PagePreview 占位 UI,但用户无法在真实渲染后的页面上交互。M1 已落地 `CrawlConfig` / `CrawlField` 实体与 REST CRUD。本 change 在两者之上打通"点击页面元素 → 候选选择器 → 匹配预览 → 保存为字段"的端到端用户闭环。`page-visual-selection` spec 上半部分(浏览器会话生命周期、实时截图推送、加载状态、点击生成选择器)已定义但未实现;本 change 把 spec 上半部分 + 新增的下半部分(候选生成、匹配预览、高亮、端到端保存)一并落地。后端当前是 Spring Boot 3.2.5 + JPA + Java 21,无 Playwright 依赖、无 WebSocket 端点。

## Goals / Non-Goals

**Goals:**
- 把 `page-visual-selection` spec 上半部分(Playwright 单会话 + 截图推送 + 加载状态)从 spec 落到实现
- 把 spec 新增的下半部分(点击 → 候选 → 匹配预览 → 高亮 → 保存字段)一并落地
- 端到端用户闭环:URL → 加载 → 点击 → 候选 → 预览 → 保存
- 与 M1 现有 Field CRUD 复用,无数据模型变更
- 70 个 M1/M2 测试 + 新增测试全绿

**Non-Goals:**
- 抽取模板、爬取执行、采集结果保存
- 多元素点击合成选择器、字段类型校验、selector_type 切换失效提示、结构变化主动巡检
- `list_url` / `detail_url` 入库
- iframe / shadow DOM 内点击
- 多租户多会话并发
- 现有 `POST /api/v1/page-fetch` 端点的移除或语义变更(向后兼容)

## Decisions

### D1. Playwright Bean 单例 + 单 Page

**选型**:`com.microsoft.playwright:playwright` 1.49.x(Java 客户端)+ 内嵌 Chromium(随依赖拉取)。
**结构**:`BrowserSessionService` 作为 Spring `@Service` 单例,内含 `Playwright` / `Browser` / `BrowserContext` / `Page` 四个字段,所有字段在 `@PostConstruct` 时创建,`@PreDestroy` 时关闭。同一时间最多 1 个 Page(打开新 URL 时先 `page.close()` 再 `browser.newPage()`)。
**理由**:Spring 单例天然解决"同一时间只有一个会话"的硬约束;`@PreDestroy` 在 JVM 关闭时保证 Playwright 进程不残留。
**替代考虑**:多 Browser 实例 + 池化(被 spec "单租户单会话" 否决)。

### D2. WebSocket 协议:原生 Spring `@MessageMapping` 风格,统一消息信封

**选型**:所有消息用 `record` 形态的 `WsMessage<T>(String type, T payload)` 信封;客户端 → 服务端消息类型 `load` / `click` / `preview` / `saveField` / `close`;服务端 → 客户端消息类型 `screenshot` / `state` / `selectors` / `previewResult` / `saveFieldResult` / `error`。
**路由**:Spring `@MessageMapping("/page")` + `SimpMessagingTemplate.convertAndSend(...)` 推到 `user:/queue/page/{sessionId}`。每个 WebSocket session 在 `WebSocketHandler` 中分配 `sessionId` 并绑定到 `configId`(由 `load` 消息携带)。
**理由**:信封化消息便于扩展(未来加消息类型不改协议);`sessionId` + `configId` 在握手后绑定,避免每条消息重复带 configId。
**替代考虑**:STOMP(被否决,团队目前无 STOMP 经验,引入学习成本高于收益);裸 WebSocket 文本协议(被否决,无类型约束,易写错字段名)。

### D3. CSS 候选:`com.github.csselector:csselector` 不可用,改用 `dev.failsafe:css-selector-generator` 0.1.x(纯 Java,无 JS 依赖)

**选型**:`io.github.failsafe-oss:css-selector-generator:0.1.3` Maven 坐标;调用 `CssSelectorGenerator.generateSelector(Element el) -> String`。
**理由**:Maven Central 可直接拉;纯 Java 实现,无 Node.js 依赖;支持 id / 唯一 class / 兄弟结构 / data-* 反推;License: Apache-2.0。
**替代考虑**:`org.jsoup:jsoup`(不支持反推,只能解析);`com.googlecode.cssparser`(只能解析不能生成);自写 CSS 反推(被否决,bug 多)。
**回退**:若该库在 Windows 上拉取失败,改用 `io.github.failsafe-oss:css-selector-generator:0.1.0` 版本或换用 `com.codeborne:selenide` 的 `By.cssSelector` 自写(本期不实现,作为风险)。

### D4. XPath 候选:自写反推算法

**算法**:从 element 沿 `getXPath()` 向上爬,每个节点判断:
- 是否有 `id` 属性 → 用 `//*[@id='x']`(整棵树唯一,直接终止)
- 否则看同级中是否唯一 → 拼 `tagName`(不写谓词)
- 否则在同级位置标 `tagName[1-based index]`(仅在 tag 重复时)
- 用 `/` 或 `//` 拼根路径(根用 `//` 以容忍页面深度变化)
- 属性谓词:若元素有 `class`,追加 `[contains(@class,'x')]`(避免 `@class='x y'` 严格匹配)
**理由**:XPath 表达力强但反推没有现成 Java 库,自写可控;算法复杂度低,单测容易覆盖。
**替代考虑**:引入 `org.apache.xmlbeans`(太大);`com.googlecode.xpath`(老旧)。

### D5. 匹配高亮:在真实 DOM 上注入 `.vs-highlight` class

**实现**:`page.evaluate("(selector) => { document.querySelectorAll('.vs-highlight').forEach(e => e.classList.remove('vs-highlight')); document.querySelectorAll(selector).forEach(e => e.classList.add('vs-highlight')); return document.querySelectorAll(selector).length; }", selector)`,然后 `page.screenshot()`。
**样式**:`outline:2px solid #ff4d4f;background:rgba(255,77,79,0.15)`(在新建 Page 时通过 `page.addStyleTag(content=".vs-highlight{...}")` 注入一次)。
**理由**:真实 DOM 高亮准确,不会因截图后渲染错位;复用截图帧通道,前端无需画 div。
**替代考虑**:后端计算 bbox 数组 + 前端在截图上画 div(被否决,DPR / 缩放 / iframe 视口对齐复杂)。

### D6. 坐标变换:1:1 视口 + deviceScaleFactor=1,前端不做换算

**约束**:`application.yml` 默认 `playwright.viewport.width=1280` / `height=800` / `device-scale-factor=1`;前端截图 `<img>` 1:1 渲染(`width="1280"` 不写 `style="width:50%"`)。
**理由**:消除 DPR / 缩放带来的坐标换算;CSS 像素与图像像素直接对应,`elementFromPoint(x, y)` 直接用。
**替代考虑**:响应式 viewport(被否决,换算增加测试负担)。

### D7. 选择器落库复用 M1 现有 `CrawlFieldService.create`

**路径**:WebSocket 收到 `saveField` → 在 handler 内调 `CrawlFieldService.create(configId, new CreateFieldRequest(pageType, fieldName, fieldType, selector))`。
**理由**:不绕开 service,自动复用 M1 的校验(name 唯一、selector 非空、pageType 合法)、级联、updated_at 维护。
**替代考虑**:直接调 `CrawlFieldRepository.save(...)`(被否决,跳过 service 校验 + 跨 aggregate 事务)。

### D8. WebSocket 单 session 内 configId 绑定

**绑定时机**:WebSocket `afterConnectionEstablished` 时 `sessionId` 入 map;首条 `load` 消息携带 `configId`,放入 `sessionId → configId` map;后续 `click` / `preview` / `saveField` 均不需带 configId(从 map 拿);`afterConnectionClosed` 时清理。
**理由**:减少每条消息冗余,避免前端忘带。
**替代考虑**:每条消息都带 configId(被否决,易出错)。

### D9. Playwright 启动失败提示

**机制**:`@PostConstruct` 中 try-catch Playwright 启动,若失败,打印与 PG 启动失败同风格的多行 banner 提示用户手工安装 Chromium(`mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`),同时不阻止 Spring 上下文启动(允许 `/health` 返回),但 `BrowserSessionController.open()` 返回 503。
**理由**:与现有 PG 启动 banner 一致,体验统一。
**替代考虑**:Playwright 启动失败直接抛 BeanCreationException 让应用退出(被否决,体验差)。

## Risks / Trade-offs

- **R1**:Playwright 内嵌 Chromium 体积 ~300MB,开发机 / CI 需预留磁盘 → Mitigation:在 `docs/runbook.md` 加"Playwright 启动"段,CI 镜像 base 包含 chromium
- **R2**:`css-selector-generator` 库是社区项目,长期维护不确定 → Mitigation:封装在 `SelectorCraftService` 后,若库失效,只改 service 实现
- **R3**:WebSocket 无鉴权(开发期同源,无 token)→ Mitigation:生产化时(下个 change)再补 JWT / Cookie 校验
- **R4**:单 Page 限制下,两个用户同时点开 PagePreview 会冲突 → Mitigation:spec 显式声明单租户限制,UI 在 `BrowserSessionService.alreadyActive()` 时给提示
- **R5**:`elementFromPoint` 不跨 iframe / shadow DOM → Mitigation:spec 显式声明,前端 UI 给用户提示
- **R6**:截图帧推送频率(1 秒 1 帧)在网络差时延迟明显 → Mitigation:`state=LOADED` 在加载完成瞬间推一次(不等下一帧定时器),`screenshot` 在 `page.screenshot()` 完成后立即推,不强制 1 秒周期
- **R7**:XPath 反推算法在嵌套结构 / 重复 class 场景下可能选出不稳定路径 → Mitigation:单测覆盖嵌套 5 层 / class 重复 / id 缺失 3 类场景,失败 case 留作下个 change 优化
- **R8**:Chromium 进程残留(JVM 异常退出时)→ Mitigation:`@PreDestroy` 兜底,Windows 上 `tasklist /FI "IMAGENAME eq chrome.exe"` 人工检查文档化

## Migration Plan

无数据库变更,无 API 路径变更,无破坏性变更。部署:
1. `mvn clean package -DskipTests` 打 jar
2. 首次启动前执行 `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`(Windows 同)
3. 启动后端,验证 `/api/v1/health` 返回 200,`GET /api/v1/browser/sessions` 返回 200 `{active:false}`
4. 启动前端 `npm run dev`,访问 `/configs/:id/preview`,输入 URL,执行点击 → 候选 → 预览 → 保存全链路
5. 回归:`mvn test`(M1/M2 70 测试 + 新增),`npm test`(M2 8 测试 + 新增)

回滚:删除 `com.microsoft.playwright:playwright` 依赖 + 删除新增文件 + 删除 `page-fetch` 之外的 `BrowserSessionController` 路由;M1/M2 端点不受影响。

## Open Questions

无(已与用户确认)。实施过程中如发现反推算法对某类结构生成的选择器不稳定,会回到 explore 重新评估是否换库或扩算法,**不擅自扩大 scope**。
