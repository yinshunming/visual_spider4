## Why

可视化爬虫需要"在已加载页面上点元素 → 自动生成 CSS / XPath 候选 → 看到匹配预览 → 存为字段规则"这条端到端闭环,目前 M2 仅完成了"无头 HTTP 抓取元信息"的最小切片,无法真实渲染 JS、无法交互,字段选择器只能手写。本 change 把 M2 spec 上半部分(Playwright 单会话 + 实时截图推送 + 加载状态)从 spec 落到实现,并把闭环后半段(点击 → 候选 → 匹配预览 → 高亮 → 保存字段)一并打通,作为后续抽取模板、爬取执行的事实地基。

## What Changes

- 新增后端 Playwright 子系统:`BrowserSession` 单例 Bean(单进程、单 Page)+ `com.microsoft.playwright:playwright` 依赖 + 内嵌 Chromium
- 新增浏览器会话 REST:`POST /api/v1/browser/sessions` 打开、`DELETE /api/v1/browser/sessions/{id}` 关闭、`GET /api/v1/browser/sessions` 状态
- 新增 WebSocket 端点 `/api/v1/ws/page`:服务端推送 `screenshot`(base64 PNG)和 `state`(LOADED/ERROR);客户端发 `load`(url)、`click`(viewport x,y)、`close`
- 新增选择器生成服务 `SelectorCraftService`:
  - CSS 走 `css-selector-generator` 库
  - XPath 自写反推算法(沿 ancestor chain 拼轴 + 谓词)
  - 同时返回 2 个候选 + 每个的 `matchCount` + `samples`(前 N 条文本)
- 新增匹配预览:在真实 DOM 上 evaluate 注入 `.vs-highlight` overlay 到所有匹配元素,推送新截图帧
- 新增反馈规则:matchCount==0 红、==1 绿、>1 黄
- 新增端到端保存:前端在 `PagePreview.vue` 侧边栏填写 fieldName/fieldType/pageType/selector,调 M1 现有 `POST /api/v1/configs/{id}/fields` 落库
- 改造 `frontend/src/views/PagePreview.vue`:在截图 `<img>` 上叠透明 click 捕获层,转换截图坐标 → viewport 坐标后经 WebSocket 发给后端
- 在 `page-visual-selection` spec 追加新 Requirement 段(点击 → 候选 → 匹配预览 → 高亮 → 保存字段)
- **BREAKING**:M2 同步 HTTP 抓取 `POST /api/v1/page-fetch` 保留(向后兼容),不替换
- **BREAKING**:单租户单浏览器会话限制写入 spec,本期不实现多会话并发

## Capabilities

### New Capabilities

无。新能力全部落在已存在的 `page-visual-selection` 之下,本 change 视为该能力从"部分实现"推进到"完整闭环"。

### Modified Capabilities

- `page-visual-selection`:追加新 Requirement 段(点击元素 → 生成 CSS/XPath 候选 → 匹配数量与样本 → DOM 内高亮 → 端到端保存为 crawl_field);明确 WebSocket 协议(`screenshot`/`state`/`load`/`click`/`close` 消息形状);明确单租户单会话约束;明确 1:1 视口尺寸与设备像素比策略;明确 iframe / shadow DOM 不支持

## Impact

- **后端代码**:`backend/pom.xml` 新增 `com.microsoft.playwright:playwright`(~300MB,含 Chromium 二进制);新增 `service/BrowserSessionService`、`service/SelectorCraftService`、`service/SelectorHighlighter`、`controller/BrowserSessionController`、`ws/PageWebSocketHandler`、`dto/ws/*` 消息记录、`dto/response/SelectorCandidateResponse`、`dto/response/SelectorMatchResponse`
- **后端依赖**:Chromium 首次拉取需执行 `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` 或在启动时由 Playwright 客户端自动下载
- **后端启动**:`@PostConstruct` 触发 Playwright 启动,失败要明确报错(参考现有 PG 启动 banner 风格)
- **后端配置**:`application.yml` 新增 `playwright.*` 块(viewport.width=1280 / height=800 / headless=true / navigation-timeout=15000)
- **后端测试**:新增 BrowserSessionService / SelectorCraftService / WS 消息路由 / Highlight 注入测试;Controller 层 `BrowserSessionController` 测试
- **前端代码**:`frontend/src/views/PagePreview.vue` 大幅改造(URL 输入 + 加载按钮 + 截图展示 + click 捕获层 + 候选面板 + 字段保存表单);新增 `frontend/src/api/browser.js`(WebSocket 客户端 + 浏览器会话 REST);Pinia store `useBrowserSessionStore`;`usePageFetchStore` 保留(向后兼容 M2 抓取入口)
- **前端依赖**:无需新增(浏览器原生 WebSocket 即可,前端无构建工具更轻)
- **前端测试**:新增 `PagePreview.vue` 组件测试(WebSocket mock + click 坐标转换 + 候选选择 + 字段保存);`pageFetchStore` 现有 4 个测试不动
- **数据模型**:`crawl_config` / `crawl_field` 表**不增列**,本次仍用 M1 schema
- **范围外(冻结)**:不做抽取模板、不执行爬取、不保存采集结果、不做多元素合成选择器、不做字段类型校验、不做 selector_type 切换失效提示、不做结构变化主动巡检、不存 list_url/detail_url、不支持 iframe 内点击、不实现多租户并发
