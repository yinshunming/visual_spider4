## ADDED Requirements

### Requirement: 按模板预览按钮启用条件

`PagePreview` 页面"按当前模板预览"按钮 SHALL 仅在浏览器会话已成功加载当前页面时处于可点击态。前端 MUST 在收到后端 `state="LOADED"` WebSocket 消息后将该按钮设为可点击；在收到 `state="ERROR"`、会话关闭、或从未加载 URL 时 MUST 设为禁用。按钮处于 disabled 态时点击 MUST NOT 发送 `previewTemplate` 消息。

判定信号 SHALL 单一来源：以前端 store 中"`status` 等于 `LOADED`"为准(对应后端 `ws/PageWebSocketHandler.handleLoad` 发出的 `StatePayload.loaded()`)。按钮在预览请求飞行期间(等待 `previewTemplateResult` 回包)亦 MUST 处于禁用态(由 `extractionPreviewStore.isLoading` 控制)。

#### Scenario: 页面加载成功后按钮变为可点击

- **WHEN** 用户在 `PagePreview` 页面输入 URL 并点击"加载"，后端处理完成后推送 `{type:"state", payload:{state:"LOADED"}}`
- **THEN** 前端 store 的 `status` 字段置为 `"LOADED"`，"按当前模板预览"按钮 disabled 解除，用户可点击触发预览

#### Scenario: 页面加载失败时按钮保持禁用

- **WHEN** 后端推送 `{type:"state", payload:{state:"ERROR", message:"..."}}`
- **THEN** 前端 store 的 `status` 字段置为 `"ERROR"`，"按当前模板预览"按钮 MUST 保持 disabled；同时顶部展示错误提示

#### Scenario: 尚未加载任何 URL 时按钮禁用

- **WHEN** 用户进入 `PagePreview` 页面但未触发过"加载"动作
- **THEN** store 的 `status` 字段为初始值(`"idle"`),非 `"LOADED"`；"按当前模板预览"按钮 MUST 处于 disabled

#### Scenario: 预览请求飞行期间按钮禁用

- **WHEN** 用户点击"按当前模板预览"按钮后,等待 `previewTemplateResult` 回包期间
- **THEN** 按钮 MUST 处于 disabled 态(由 `extractionPreviewStore.isLoading === true` 驱动);期间再次点击 MUST NOT 发送 `previewTemplate` 消息
