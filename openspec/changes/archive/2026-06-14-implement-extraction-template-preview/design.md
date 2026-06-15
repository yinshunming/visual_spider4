## Context

可视化爬虫 MVP 已经完成 M1(配置/字段 CRUD) + M2(HTTP 同步页面元信息抓取) + M2.5(Playwright 单会话 + WebSocket 端到端单字段造选择器闭环)。当前数据库里只有 `crawl_config` 与 `crawl_field` 两张表,**没有任何爬取产物**(list_page / article / raw_html)。M3 在这个状态上加一层"按模板批量预览"能力,目标是用户在进入 M4 真实爬取之前,能在调试态对自己已经存好的一组字段做整体回归。

技术约束:

- 浏览器会话 spec 规定**进程内单例**(`BrowserSessionService` 单例 Bean,单 Page),M3 不能引入并发会话
- WebSocket `/api/v1/ws/page` 通道 spec 已锁定消息 `WsMessage<T>{type, payload}` 信封,新增消息必须复用该信封
- 字段类型校验 spec(`extraction-template`)已锁定 4 种 `FieldType`(TEXT / NUMBER / DATE / URL)与"非法值置 null"语义,不能引入新类型也不能改变非法值语义
- LIST_DETAIL 配置缺 `detail_url` 字段 spec(`selector-rule-management`)已锁定"阻止启动爬取任务",注意原文是**爬取任务**而非预览,M3 在预览阶段做软提示不违反 spec
- 后端测试不接 Testcontainers(项目惯例),Playwright 相关测试用 mockito-extensions/mock-maker-inline 走 mock(参考已有 `BrowserSessionServiceTest`)

利益方:

- 用户:在 M4 之前自助验证字段配置正确性
- 后端开发者:为 M4 爬取执行提供一个可复用的"批量字段提取 + 校验"内核(`ExtractionService` 在 M4 直接被爬取流程调用即可,无需重写)
- 前端开发者:在不改变 M2.5 现有造字段流程的前提下,通过 Tab 容器加新视图,对老逻辑零改动

## Goals / Non-Goals

**Goals:**

- 在已加载的 Playwright Page 上,对某 page_type(LIST 或 DETAIL)下所有已存字段批量执行选择器,返回结构化结果
- 对每个字段返回 `matchCount` + `rawValues`(原始字符串数组) + `validatedValues`(类型校验后) + `status` 四态(`OK` / `TYPE_MISMATCH` / `NO_MATCH` / `SELECTOR_INVALID`)
- 提供一个**可在 M4 直接复用**的字段提取内核(`ExtractionService` + `FieldValueValidator`),不与 WebSocket 协议绑死
- 前端在 `/configs/:id/preview` 同一页面通过 Tab 切换提供"造字段 / 按模板预览"两种工作模式,共享同一 BrowserSession
- 为 M4 爬取执行预留干净的接口边界:M4 拿到 `Page` + `configId` + `pageType` 直接调 `ExtractionService.extractByTemplate(...)` 即可,不依赖 WebSocket

**Non-Goals:**

- 不实现"对历史 raw_html 重新解析"。原因:数据库里没有 raw_html,该 Requirement 推迟 M4
- 不实现"启动爬取任务时强制 detail_url 校验"。该 spec 原文限定爬取任务,M3 软提示即可
- 不做任何爬取 / 任务调度 / 结果落库
- 不做 selector_type 切换失效自动检测与提示
- 不做跨站点模板复用 / 多元素合成选择器
- 不支持 iframe 内 / shadow DOM 内字段(与 M2.5 一致)
- 不引入预览结果的流式分块返回(本期接受 1-2 秒同步延迟)
- 不引入预览结果导出 / 持久化(预览是一次性调试动作)

## Decisions

### Decision 1: 入口走"同页 Tab 切换",不走独立路由

**选择**:在 `/configs/:id/preview`(即现有 `PagePreview.vue`)内增加 Tab 容器,Tab1 = M2.5 造字段流程(原封不动),Tab2 = M3 按模板预览。两 Tab 共享同一 `BrowserSession` + 同一 URL 输入框 + 同一截图区(顶部公共区)。

**理由**:

- BrowserSession 单例约束:独立路由切来切去会反复关 Page 重开,体验差且浪费资源
- 用户工作流自然:"看截图 + 造字段"和"看截图 + 跑模板"用的都是同一个已加载页面,把它们物理拆开反而需要用户重复输入 URL 加载页面
- M2.5 现有代码零改动(Tab1 直接复用)

**替代方案**:

- 独立路由 `/configs/:id/extract-preview`:被 BrowserSession 单例约束否决
- Modal 弹窗:不能并存看截图与结果表

### Decision 2: 通道走 WebSocket 新消息 type,不开 REST

**选择**:WebSocket `/api/v1/ws/page` 增加 `previewTemplate` (客户端→服务端) 与 `previewTemplateResult` (服务端→客户端) 两类消息。

**理由**:

- BrowserSession 状态(`Page` + `configId`)已经在 WebSocket session 里绑定(`sessionToConfig`),沿用同一通道天然有上下文
- 与 M2.5 已有的 `load` / `click` / `preview` / `saveField` / `close` 五类消息形态一致,handler 只需多一个 switch 分支
- REST 拿不到当前活跃 Page,需要再去单例 Bean 里取,反而绕

**替代方案**:

- REST `POST /api/v1/configs/:id/extract-preview`:被"REST 拿不到 Page 上下文"否决
- gRPC / SSE:与项目其余通信风格不一致

### Decision 3: 字段提取内核独立服务,不直接写在 WS Handler 里

**选择**:新建 `ExtractionService.extractByTemplate(Page page, Long configId, FieldPageType pageType)` 与 `FieldValueValidator.validate(String raw, FieldType type)`,WS Handler 只负责协议解析与响应封装。

**理由**:

- M4 爬取执行会在循环里反复调用同一段批量提取逻辑,如果绑死在 WS Handler 里,M4 必须重写
- 单元测试更容易:`ExtractionService` 接受抽象 `Page`,可以 mock;`FieldValueValidator` 是纯函数,测试边界值极快
- WS Handler 已经因 M2.5 比较厚(271 行),不再继续堆

**替代方案**:

- 写在 WS Handler 内:被"M4 复用 + 测试粒度"否决

### Decision 4: 字段状态明示四态,而非二态

**选择**:`FieldPreviewStatus` 枚举 = `OK` / `TYPE_MISMATCH` / `NO_MATCH` / `SELECTOR_INVALID`。

| 状态 | 触发条件 | rawValues | validatedValues |
|---|---|---|---|
| `OK` | 选择器命中 ≥1 个 + 全部校验通过 | 原始字符串数组 | 与 raw 等长,值合法 |
| `TYPE_MISMATCH` | 命中 ≥1 个 + 至少 1 个校验失败 | 原始字符串数组 | 失败位置为 `null` |
| `NO_MATCH` | 选择器命中 0 个 | `[]` | `[]` |
| `SELECTOR_INVALID` | 选择器在 Page.evaluate 抛错 | `[]` | `[]` + `message` 含错误 |

**理由**:

- 用户视角:"压根没匹配上"和"匹配上了但类型不对"是完全不同的修复路径(前者改选择器,后者改字段类型),必须分开
- 前端 UI 视觉:四态各对应不同颜色/图标,辅助判断
- 与 spec `extraction-preview-validation` "字段级部分成功"语义对齐(spec 不要求四态,但四态是兼容超集)

**替代方案**:

- 二态 OK / NULL:被"区分能力差"否决
- 三态(吞掉 SELECTOR_INVALID,归到 NO_MATCH):被"无法定位选择器语法 bug"否决

### Decision 5: 多值字段统一数组形态

**选择**:无论 matchCount 是 0 / 1 / N,`rawValues` 与 `validatedValues` 都是数组。前端按 `length` 决定如何渲染。

**理由**:

- 与 `extraction-template` spec "多值字段提取"完全一致("以 JSON 数组形式存储")
- 后端类型固定,代码无 if 分支
- 前端只需一处 `length === 1 ? show[0] : showList`

**替代方案**:

- 单值标量 + 多值数组(动态类型):被"前端类型判断成本"否决

### Decision 6: URL 字段优先取 `.href`,非链接元素退回 `textContent`

**选择**:在 `ExtractionService` 内,字段类型为 `URL` 时,page.evaluate 取 `el.href ?? el.textContent`(浏览器 DOM 的 `.href` 自动绝对化相对路径)。其他类型字段统一取 `textContent.trim()`。

**理由**:

- 用户体验:大多数 URL 字段(尤其 detail_url)选择器命中的就是 `<a>`,自动绝对化省去用户手工拼 base URL
- 退回逻辑:用户偶尔会选错命中 `<span>`,此时取 `textContent`,校验阶段大概率 TYPE_MISMATCH(用户能看到原始值)
- 不引入"自动拼 base URL"的复杂度,所有绝对化都来自浏览器 DOM 自身

**替代方案**:

- 一律取 `textContent`:被"用户体验"否决
- 服务端拼接 base URL:被"复杂度 + base 来源不明"否决,留给 M4 爬取阶段考虑

### Decision 7: 类型校验细则锁死

| FieldType | 合法判定 | 非法处理 |
|---|---|---|
| TEXT | trim 后非空 | 空字符串归 `NO_MATCH`(不进 validatedValues) |
| NUMBER | `Double.parseDouble(raw)` 不抛 | 抛 → null + TYPE_MISMATCH。**不接受千分位** `1,234` |
| DATE | `DateTimeFormatter.ISO_DATE.parse` 或 `ISO_DATE_TIME.parse` 不抛 | 抛 → null + TYPE_MISMATCH。**严格 ISO 8601** |
| URL | `new URI(raw).isAbsolute()` 且 scheme ∈ {http, https} | 否则 → null + TYPE_MISMATCH |

**理由**:

- 严格规则边界清晰,用户能预测
- spec 原文未具体到这一层,但写在 design 里把规则锁死,避免实施时反复

**替代方案**:

- NUMBER 接受千分位 / DATE 做 fuzzy parse:被"宽松解析失败时用户更困惑"否决,留给后续如果有真实需求再放宽

### Decision 8: 空模板返回 fields:[] + warnings 软提示

**选择**:用户对零字段的 page_type 触发预览,后端正常返回 `fields:[]` + `warnings:["该模板未定义任何 <pageType> 字段"]`,不报错。

**理由**:

- spec `extraction-template` "字段集不完整"明确"允许零字段定义而不阻止爬取执行",预览同此
- 用户工作中确实会"先建配置后慢慢加字段",报错打断流程

### Decision 9: detail_url 缺失走顶部软警告横幅

**选择**:LIST_DETAIL 配置 + 用户切到 LIST 页面预览 + 字段集中没有 `field_name="detail_url"` 且 `field_type=URL` 的条目时,后端在 response payload 里附加 `warnings:["LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截"]`,前端在结果表上方展示黄色警告横幅。**不阻止预览**。

**理由**:

- spec 原文只阻止"爬取任务",不阻止预览
- 用户在调试阶段需要逐步加字段,过早阻止会打断工作

**替代方案**:

- 直接拒绝预览:被"调试体验"否决
- 不提示:被"M4 才发现配置错误"否决

### Decision 10: 单次预览同步执行,不分块流式返回

**选择**:服务端在收到 `previewTemplate` 后,对所有字段顺序执行,**全部完成后**一次性发回 `previewTemplateResult`。前端在等待期间显示 loading。

**理由**:

- 实施简单,与现有协议风格一致
- 估算延迟:50-200ms / 字段 × 平均 5-10 字段 = 250ms - 2s,可接受
- 流式分块会让前端表格状态机变复杂,不值得

**替代方案**:

- 流式分块(每完成一个字段推一条):被"复杂度收益比"否决,M4 反馈不可接受再升级

## Risks / Trade-offs

- **[风险 1] 字段数量多时单次 WebSocket 响应较慢** → 接受 1-2 秒延迟,前端显示 loading;若 M4 反馈不可接受,Decision 10 升级为流式分块
- **[风险 2] page.evaluate 注入选择器若用户提供 XSS 字符串,理论上会注入到浏览器上下文** → 评估:浏览器是后端进程内的隔离 Chromium,执行的也是用户自己的选择器,不打到生产数据;输入校验上 `selector` 仅作为 querySelector 参数,不作为 innerHTML,Playwright 不会执行 JS 字符串。本期不加额外防护
- **[风险 3] 前端 Tab 切换时若 BrowserSession 已被外部 close,Tab2 触发预览会拿到 NO_SESSION 错误** → 后端按统一 `error` 消息回,前端在 Tab2 顶部明确提示"会话已关闭,请重新加载 URL"
- **[权衡 1] URL 字段 `.href` 优先策略让"用户用 URL 类型 + 选 span 元素"这种边界场景行为不那么直观** → 通过 `message` 字段在 TYPE_MISMATCH 时给出"非链接元素的 textContent 不是绝对 URL"提示,缓解
- **[权衡 2] 严格 ISO 8601 + 不接受千分位 NUMBER 会让用户在常见非标准格式上看到 TYPE_MISMATCH** → 接受。预览的目的就是暴露这种问题,让用户感知到自己抓的是非结构化文本;后续如果真实场景需要,放宽 Decision 7 即可
- **[权衡 3] M3 没有为 ExtractionService 编写"针对真实浏览器"的集成测试,全部走 mock Page** → 接受。M2.5 已经验证了 Playwright 集成路径,M3 走 mock 测试已经能覆盖逻辑分支;e2e 一个 spec 兜底全链路

## Open Questions

无。所有边界已通过 6 + 4 = 10 项决策锁死。
