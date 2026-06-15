## Why

M2.5 已经把"点元素 → 生成选择器候选 → 单字段落库"打通,但用户至今无法回答一个最基本的问题:**"我配置的这一组字段,放到一起对一张真实页面跑一遍,结果对不对?"** 当前的"按选择器单字段提取"只能验证一颗螺丝,无法验证整台机器。M3 把"已保存字段"作为整体抽取模板,在已加载的浏览器页面上一次性执行并展示结构化结果(含字段级状态),用户在进入 M4 真实爬取之前能够先在调试态发现选择器失效、类型不符、未匹配等问题,大幅降低后续爬取任务的返工成本。

## What Changes

- 新增"按模板预览"能力:在已加载的 Playwright Page 上批量执行某 page_type(LIST 或 DETAIL)下所有已保存字段的选择器,一次性返回结构化抽取结果
- 新增字段类型校验器:TEXT / NUMBER / DATE(ISO 8601) / URL(绝对 http(s)) 校验,非法值置 null,与 spec 一致
- 新增字段级状态语义:`OK` / `TYPE_MISMATCH` / `NO_MATCH` / `SELECTOR_INVALID` 四态,与"页面级失败"完全分离
- 新增多值字段数组返回:无论命中 0 / 1 / N 个元素,统一以数组形式返回 `validatedValues`
- 新增 URL 字段属性源切换:URL 类型字段优先取元素 `.href`(浏览器自然绝对化),非链接元素退回 `textContent`
- WebSocket `/api/v1/ws/page` 通道新增消息 type:`previewTemplate`(客户端 → 服务端)、`previewTemplateResult`(服务端 → 客户端)
- 前端 `PagePreview.vue` 加 Tab:Tab1 = M2.5 造字段(保留)、Tab2 = 按模板预览(新)。Tab2 共享同一 BrowserSession 与 URL,内嵌 LIST/DETAIL 切换、"按当前模板预览"按钮、字段结果表
- 前端 LIST_DETAIL 配置缺 `detail_url` 字段时:在 Tab2 顶部展示**软警告横幅**(不阻止预览)
- 前端新增 `extractionPreviewStore`(Pinia) 缓存最近一次 LIST / DETAIL 预览结果
- **范围外(冻结,推迟 M4)**:不实现"对历史 raw_html 重新解析";不实现"启动爬取任务时强校验 detail_url";不做任何爬取 / 任务调度 / 结果落库;不做 selector_type 切换失效提示;不做跨站点模板复用;不做多元素合成选择器;不支持 iframe / shadow DOM 内字段(与 M2.5 保持一致)

## Capabilities

### New Capabilities

无。本次新行为全部归属已存在的 `extraction-template` 与 `extraction-preview-validation` 两个能力,把它们从纯 spec 状态推进到部分实现状态(模板预览闭环)。

### Modified Capabilities

- `extraction-template`:追加 Requirement 段,明确"按模板批量提取"的具体协议(WebSocket 消息形状、字段级状态四态、多值数组形态、URL 字段 `.href` 优先策略、空模板容错);明确声明"raw_html 重新解析"在本 change 范围外
- `extraction-preview-validation`:追加 Requirement 段,把"实时预览"从抽象 SHALL 落到具体协议(`previewTemplate` / `previewTemplateResult` 消息);追加"字段级部分成功在预览阶段的呈现规则"(状态四态 + 错误描述);明确"页面级加载失败"复用 M2.5 现有 `state=ERROR` 通道,不重复定义

## Impact

- **后端代码**:新增 `service/ExtractionService`(批量执行入口) + `service/FieldValueValidator`(类型校验) + `enums/FieldPreviewStatus`(四态枚举);新增 DTO `dto/response/FieldPreviewResult` / `dto/response/ExtractionPreviewResponse` / `dto/ws/PreviewTemplatePayload` / `dto/ws/PreviewTemplateResultPayload`;改造 `ws/PageWebSocketHandler` 增加 `previewTemplate` 分支;复用 `BrowserSessionService.getPage()` 与 `CrawlConfigService.findById(...).getFields()`
- **后端依赖**:无新增三方依赖。完全复用 M2.5 已引入的 Playwright + Jsoup 能力,日期校验用 JDK `java.time.format.DateTimeFormatter.ISO_*`,URL 校验用 JDK `java.net.URI`
- **后端配置**:无新增配置项
- **后端测试**:新增 `FieldValueValidatorTest`(覆盖 4 种 fieldType 合法/非法边界)、`ExtractionServiceTest`(覆盖命中 0/1/N + 类型校验 + URL `.href` 优先 + 选择器非法 + 空模板)、`PageWebSocketHandlerTest` 增加 `previewTemplate` 分支测试(无 Page / configId 缺失 / 正常路径);整体后端测试预计从 101 增至约 130
- **前端代码**:`views/PagePreview.vue` 增加 Tab 容器 + Tab2 完整子树(pageType 切换 / 触发按钮 / 结果表 / 软警告横幅);`api/browser.js` 新增 `sendPreviewTemplate(pageType)` / `onPreviewTemplateResult(callback)`;新增 `stores/extractionPreviewStore.js`
- **前端测试**:新增 `extractionPreviewStore.test.js` + `PagePreview.vue` 中 Tab2 渲染断言(4 种字段状态 + 软警告);整体前端测试预计从 17 增至约 25
- **e2e**:新增 `tests/extraction-preview.spec.js`,覆盖"建配置 + 加 3 字段 → 进预览 → 加载 URL → 切 Tab2 → 触发预览 → 结果表断言"全链路
- **数据库**:**无 schema 改动**。完全复用 M1 的 `crawl_config` / `crawl_field` 表
- **文档**:更新 `AGENTS.md` 当前里程碑状态;`docs/api-guide.md` 追加 `previewTemplate` / `previewTemplateResult` WebSocket 消息形状段;`docs/architecture.md` 追加 ExtractionService 流程图;`docs/runbook.md` 无需变更
- **风险**:对耗时较长的页面,模板批量执行可能超过单次 WebSocket 消息合理时延(估计 50-200ms / 字段),N 个字段在循环中累计;若用户已存数十个字段,前端响应需要 1-2 秒。本期**接受该延迟**,不引入分块返回;若 M4 反馈不可接受,再考虑流式分块
