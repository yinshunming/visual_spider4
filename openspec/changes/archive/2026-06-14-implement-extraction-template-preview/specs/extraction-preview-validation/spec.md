## ADDED Requirements

### Requirement: 实时模板预览协议

系统 SHALL 提供"按模板预览"端到端能力:用户在 `/configs/:id/preview` 页面已通过 M2.5 加载 URL 后,选择 `pageType`(LIST 或 DETAIL),触发后端在当前 Playwright Page 上一次性执行该 `pageType` 下所有已存字段,并通过 WebSocket `previewTemplateResult` 消息返回结构化结果。**协议为同步**,服务端在所有字段执行完毕后才发回响应,期间前端展示 loading 状态。

#### Scenario: 用户切到 Tab2 触发预览
- **WHEN** 用户在 `PagePreview` 页面切到"按模板预览"Tab,选择 `pageType=DETAIL`,点击"按当前模板预览"按钮
- **THEN** 前端发送 `{type:"previewTemplate", payload:{pageType:"DETAIL"}}`,UI 进入 loading 状态;服务端在所有字段执行完毕后回复 `{type:"previewTemplateResult", payload:...}`;UI 退出 loading 并渲染结果表

#### Scenario: 预览期间用户重复点击触发按钮
- **WHEN** loading 状态下用户再次点击触发按钮
- **THEN** 按钮 MUST 处于 disabled 状态,不重复发送请求

#### Scenario: 预览结果保留至切换 pageType
- **WHEN** 用户完成一次 LIST 预览后切到 DETAIL
- **THEN** Tab2 内 LIST 结果 MAY 被替换为 DETAIL 结果(前端 store 按 pageType 存,允许覆盖);用户切回 LIST 时若再次触发会重新执行,不要求缓存

### Requirement: 字段级部分成功在预览阶段的呈现

系统 SHALL 在预览结果表中,对每个字段独立展示其 `status` 与对应视觉:

- `OK`:绿色对勾 + `validatedValues`(单值时直接展示,多值时展示数组)
- `TYPE_MISMATCH`:黄色感叹 + `rawValues`(灰色) + `message`(失败原因)
- `NO_MATCH`:红色叉 + 文案"未命中"
- `SELECTOR_INVALID`:红色叉 + 文案"选择器非法" + `message`(浏览器错误)

整张结果表的存在**不**视为某种"页面级失败"。即使所有字段都是 NO_MATCH,前端依然按表格展示,顶部不弹错误对话框。

#### Scenario: 5 个字段中 2 个 OK / 1 个 TYPE_MISMATCH / 1 个 NO_MATCH / 1 个 SELECTOR_INVALID
- **WHEN** 服务端返回上述混合结果
- **THEN** 前端结果表渲染 5 行,每行图标与文案对应其 status,**无任何全局错误提示**

#### Scenario: 全部字段均 NO_MATCH
- **WHEN** 服务端返回 5 个字段全为 NO_MATCH
- **THEN** 前端依然完整渲染结果表(5 行红叉 + "未命中"),不弹页面级错误

### Requirement: LIST_DETAIL 缺 detail_url 字段的软警告

系统 SHALL 在 LIST_DETAIL 模式 config 触发 `pageType=LIST` 预览时,**额外**检查字段集中是否存在 `field_name="detail_url"` 且 `field_type=URL` 的条目。若不存在,服务端在 `previewTemplateResult` 的 `warnings` 数组中追加文案 `"LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截"`。前端在结果表上方展示**黄色警告横幅**。该警告 MUST NOT 阻止预览本身。

#### Scenario: LIST_DETAIL 缺 detail_url
- **WHEN** config 为 LIST_DETAIL,LIST 字段中无 `detail_url`,触发 LIST 预览
- **THEN** 服务端返回 `warnings:["LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截"]`,`fields` 仍正常返回所有 LIST 字段执行结果;前端展示黄色警告横幅 + 完整结果表

#### Scenario: DETAIL_ONLY 配置不触发该警告
- **WHEN** config 为 DETAIL_ONLY,触发任意 pageType 预览
- **THEN** 服务端**不**附加 detail_url 缺失警告(DETAIL_ONLY 模式不依赖 detail_url)

#### Scenario: LIST_DETAIL 已有 detail_url
- **WHEN** config 为 LIST_DETAIL,LIST 字段中存在 `field_name="detail_url"` + `field_type=URL` 条目
- **THEN** `warnings` 中不出现 detail_url 相关文案

### Requirement: 页面级失败复用 M2.5 ERROR 通道

预览阶段的"页面级失败"(BrowserSession 未打开 / 当前 Page 已关闭 / 浏览器进程异常)SHALL 复用 M2.5 已定义的 `{type:"error", payload:{code, message}}` 消息通道,不引入预览专用的 ERROR 消息形态。`previewTemplateResult` 仅承载"字段级"结果。

#### Scenario: 预览前 BrowserSession 未打开
- **WHEN** 客户端在没有活跃 Page 时发送 `previewTemplate`
- **THEN** 服务端返回 `{type:"error", payload:{code:"NO_SESSION", message:"浏览器未就绪"}}`,**不发送** `previewTemplateResult`

#### Scenario: 预览中浏览器进程崩溃
- **WHEN** 在执行字段抽取过程中 Playwright Page 抛出 `Target page, context or browser has been closed` 类异常
- **THEN** 服务端返回 `{type:"error", payload:{code:"NAVIGATION_FAILED" 或类似, message:<异常描述>}}`,**不发送** `previewTemplateResult`

### Requirement: 前端 Tab 容器与共享浏览器会话

系统 SHALL 在 `PagePreview.vue` 引入 Tab 容器,Tab1 = M2.5 造字段流程(原封不动),Tab2 = M3 按模板预览。两 Tab MUST 共享同一 BrowserSession、同一已加载 URL、同一截图区。Tab 切换 MUST NOT 触发 BrowserSession 关闭、新建,或 Page 重新加载。

#### Scenario: 用户在 Tab1 加载 URL 后切到 Tab2
- **WHEN** 用户在 Tab1 输入 URL 并加载成功,然后切到 Tab2
- **THEN** Tab2 直接基于当前 Page 工作,**不要求**重新输入 URL 或重新加载

#### Scenario: Tab 切换不影响截图区
- **WHEN** 用户在 Tab1 / Tab2 间切换
- **THEN** 顶部公共截图区**保持当前帧**,不重置不重连

### Requirement: extractionPreviewStore 缓存最近一次结果

前端 SHALL 提供 `useExtractionPreviewStore`(Pinia)缓存最近一次按 pageType 划分的预览结果,Tab2 组件**禁止**直接 axios / WebSocket 调后端。Store API 至少包含:`triggerPreview(pageType)`、`getResult(pageType)`、`isLoading`、`getWarnings(pageType)`。

#### Scenario: Tab2 组件触发预览
- **WHEN** 用户点击触发按钮
- **THEN** 组件调用 `store.triggerPreview(pageType)`,**不**直接发送 WebSocket 消息;store 内部封装 WebSocket 通信

#### Scenario: 预览结果在 store 中按 pageType 隔离
- **WHEN** 用户先后触发 LIST 与 DETAIL 预览
- **THEN** `store.getResult("LIST")` 与 `store.getResult("DETAIL")` 各自返回对应结果,互不覆盖
