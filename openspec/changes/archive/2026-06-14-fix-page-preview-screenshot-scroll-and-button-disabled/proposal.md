## Why

M3 模板预览闭环已交付(42/42 任务完成),但前端 `/configs/:id/preview` 在两个 UI 细节上有 bug,直接阻塞"按模板预览"这条主链路的实际可用性:

1. **Tab1 "可视化造字段"截图无法滚动**:`<img width="1280">` 渲染在无 `overflow` 约束的容器内,当浏览器视口 < 1280px(或 `.page-preview` 容器宽度过窄)时,图片右侧被裁切,且没有滚动条可访问到完整截图。点击定位选择器依赖**原始 1280 分辨率**,所以不能直接缩小图片,必须给容器加滚动条。
2. **Tab2 "按当前模板预览"按钮永远 disabled**:`isPreviewDisabled` 用了 `status !== 'ACTIVE' && !currentUrl` 这条**与后端真实状态机对不上**的条件,导致即使页面已成功加载,按钮也始终是灰色。

**为什么现在修**:这是 M3 主链路的最后一公里,9.1 手动冒烟验收时一定会立刻发现。不修,M3 等于"代码完成、用户无法使用"。E2E 没跑通(tasks 7.4 环境 blocker)使得这两个 bug 漏到当前,本次顺手收口。

## What Changes

- **修复** `frontend/src/views/PagePreview.vue`:`.screenshot-block` 加 `overflow: auto + max-width: 100% + max-height: 70vh` 容器,让原始 1280px 截图在小视口下通过滚动条可达
- **修复** `frontend/src/views/PagePreview.vue`:`isPreviewDisabled` 从 `status !== 'ACTIVE' && !currentUrl` 改为 `!browserStore.isLoaded`,与 `design.md` / `tasks.md` 的契约对齐
- **修复** `frontend/src/stores/browserSessionStore.js`:新增 `isLoaded` getter(`status === 'LOADED'`,对应后端 `state="LOADED"` 消息);`openSession()` 补读 `resp.data.currentUrl`(后端响应里已有,前端漏读)
- **新增** `frontend/src/views/PagePreview.test.js`:为新的启用条件加 1 条 RED 测试(`status='LOADED'` → `isPreviewDisabled === false`),作为 R1 红线要求的回归保护
- **不做**:不动后端、不动 `extractionPreviewStore.js`、不动现有 spec 文本(此 change 是 UI 实现的 bug 修复,spec 层行为未变)、不动 M2.5 既有逻辑、不动 e2e(环境 blocker 保持现状)

## Capabilities

### New Capabilities

无。本 change 是纯 UI 实现层 bug 修复,无新功能、无 spec 层行为变化。

### Modified Capabilities

- `extraction-preview-validation`:追加一条 Requirement "按模板预览按钮启用条件",明确"在已加载的浏览器页面上,前端 SHALL 在收到后端 `state="LOADED"` 消息后将'按当前模板预览'按钮设为可点击;在收到 `state="ERROR"` 或会话关闭时 SHALL 设为禁用"。这是本次修复引入的最小 spec 化——把"按钮启用条件"从纯实现细节提升为可测试契约,以便 e2e 与单测都能从该 spec 出发断言。

说明:之前的探索结论是"按钮启用条件是 UI 细节,不动 spec",但 spec-driven schema 要求 `specs/` 目录下至少有一个 delta 文件才能 apply;经权衡,把"按钮启用条件"作为一条小 spec requirement 写入是**改动小、意图清晰**的选择,而不是扩大变更范围。其他两个相关 spec(`extraction-template`、`page-visual-selection`)仍**不修改**——它们描述的是协议层/会话层,不涉及按钮 UI 行为。

## Impact

- **前端代码**:3 个文件改动,总计约 25 行(其中 15 行是新增测试)
  - `PagePreview.vue`:style 段 +4 / -1 行(CSS 调整);script 段 `isPreviewDisabled` 改 1 行
  - `browserSessionStore.js`:新增 `getters` 段 ~3 行;`openSession` 补 1 行 `this.currentUrl = resp.data.currentUrl || null`
  - `PagePreview.test.js`:新增 1 条测试 ~15 行
- **后端代码**:**无改动**
- **数据库 / DTO / 协议**:**无改动**
- **依赖**:**无新增**
- **测试**:
  - `npm test -- PagePreview.test.js`:既有 9 条 + 新增 1 条 = 10 条全绿
  - `npm test` 全量:25 条(M3 完成时)→ 26 条,无回归
  - e2e 维持现状(环境 blocker 已知)
- **风险**:
  - 截图容器 `max-height: 70vh` 在超长页面下需要**内部滚动 + 外部滚动两层**;已与用户确认接受此 UX
  - `isLoaded` getter 依赖后端 `state="LOADED"` 消息契约(`PageWebSocketHandler.java:117` 持续发出),契约不变,纯前端包装,无新风险
  - `currentUrl` 字段现在被填充但**当前没有别处消费**,属于"顺手修掉的死代码读取",不扩大公开 API
- **红线符合性**:
  - R1 RED-first:新增测试先写、跑红、再写实现
  - R2 不 mock 内部:本次纯前端 CSS/computed/store 调整,无后端 mock 涉及
  - R5 不超出 spec:不在 spec 文本里加新 SHALL
