## Context

M3 模板预览闭环已交付,但 `/configs/:id/preview` 前端在两个 UI 细节上存在实现 bug,直接阻塞主链路。E2E 没跑通(tasks.md 7.4 环境 blocker)使得这些 bug 漏到当前。本次是纯前端 UI 修复,不动后端、不动 spec。

**当前状态**:
- `PagePreview.vue` `<img width="1280">` 在无 `overflow` 约束的 `.screenshot-block` 内渲染,小视口下右侧被裁切且无滚动条
- `PagePreview.vue` `isPreviewDisabled = extractionStore.isLoading || browserStore.status !== 'ACTIVE' || !browserStore.currentUrl` 与后端真实状态机不匹配
- `browserSessionStore.js` 缺 `isLoaded` getter;`openSession()` 漏读 `resp.data.currentUrl`
- `PagePreview.test.js` 缺对按钮启用条件的正向断言

**约束**:
- 截图 `width="1280"` 必须保留(点击定位选择器依赖原始分辨率与坐标系)
- 后端 `state="LOADED"` 消息契约不变(`PageWebSocketHandler.java:117`)
- `BrowserSessionResponse.currentUrl` 字段已存在(`controller/BrowserSessionController.java:27-28`),只需前端正确读取

**利益方**:
- 用户:能在小视口看到完整截图,能在页面加载后触发模板预览
- 后续维护者:`isLoaded` getter 是对后端 `LOADED` 状态的纯前端语义包装,意图清晰,比内联判断更可读

## Goals / Non-Goals

**Goals**:
- 让原始 1280px 截图在小视口下可通过容器内部滚动条完整访问
- 让"按当前模板预览"按钮在页面成功加载后(`state="LOADED"`)可点击
- 加 1 条 RED 测试覆盖新启用条件,作为 R1 红线要求的回归保护
- 改动限定在前端 3 个文件,严格最小化

**Non-Goals**:
- 不重构 Tab 整体布局、不重命名 CSS class、不改 placeholder 文案
- 不动后端 / DTO / spec 文本
- 不修 e2e 环境层 blocker(`tasks.md 7.4`,超出本次范围)
- 不动 `extractionPreviewStore.js` 与 `extractionService` 相关逻辑
- 不把"按钮启用条件"提升为 spec 级契约(若要 spec 化,另起 change)

## Decisions

### Decision 1: 截图容器采用 `overflow: auto + max-width: 100% + max-height: 70vh`,不缩放图片

**选择**:`.screenshot-block` 加 `overflow: auto; max-width: 100%; max-height: 70vh; border: 1px solid #ddd;`;`.screenshot` 删除 `border`(由父容器接管);`<img width="1280">` 保持不变。

**理由**:
- 点击定位选择器(`onImgClick` 用 `evt.offsetX/Y`)依赖图片 1:1 像素映射;缩放会破坏坐标系
- `overflow: auto` 在容器放得下时不显示滚动条,放不下时自动出现横/纵滚动条
- `max-width: 100%` 防止图片撑出 `.page-preview` 容器(`max-width: 1400px; padding: 20px`)
- `max-height: 70vh` 防止超长页面截图把整个 Tab 内容挤到屏幕外;若用户需要看完整长截图,容器内可垂直滚动
- 边框从图片转移到容器,视觉等效(容器框 + 图片贴边),不引入新视觉风格

**替代方案**:
- `max-width: 100%; height: auto` 缩放图片:被"点击坐标系被破坏"否决
- 只加 `overflow-x: auto` 不限高:被"超长页面会无限拉长页面"否决
- 改用 `<canvas>` 渲染 + 缩放控件:超出本期范围,引入新依赖风险

### Decision 2: 按钮启用条件走 `isLoaded` getter,而不是修复 `status !== 'ACTIVE' && !currentUrl`

**选择**:`browserSessionStore` 新增 `getters: { isLoaded: (state) => state.status === 'LOADED' }`;`isPreviewDisabled` 改为 `extractionStore.isLoading || !browserStore.isLoaded`。

**理由**:
- 与 `design.md` / `tasks.md` 中 M3 的契约原文对齐(`disabled 条件 = isLoading || !browserSessionStore.isLoaded`),不是发明新条件
- `isLoaded` getter 语义清晰:"页面已成功加载" = 后端发过 `state="LOADED"` 消息
- `currentUrl` 字段仍保留并修复读取(`openSession` 补 `this.currentUrl = resp.data.currentUrl || null`),但**不再作为启用条件的判据**——避免它未来被移除/改名时破坏按钮逻辑
- 单一信号源:状态机 + getter,易于单元测试(直接 `store.status = 'LOADED'` 即可断言)

**替代方案**:
- 内联 `browserStore.status !== 'LOADED'`:可工作但失去 getter 命名带来的可读性,且与 design.md 契约不一致
- 修 `openSession` 读 `currentUrl` 并用 `!currentUrl` 作启用条件:依赖一个**不在 spec 里**的字段,且 `currentUrl` 在多次加载同一 session 时可能不变,信号不可靠
- 监听后端 `state` 消息实时计算:与 getter 方案等价,但代码更分散

### Decision 3: 不动现有 spec,本次为纯实现层修复

**选择**:`openspec/specs/extraction-template/spec.md`、`extraction-preview-validation/spec.md`、`page-visual-selection/spec.md` 均**不修改**。本 change 不创建任何 `specs/<name>/spec.md` 文件。

**理由**:
- spec 描述"系统 SHALL 做什么",不描述"按钮何时 disabled"这种 UI 渲染细节
- 当前 bug 是**实现与 design.md 契约偏离**,不是"design 与 spec 偏离"——修实现即可,无需动 spec
- 若要 spec 化"按钮启用条件"(例如"当且仅当后端发送过 `state="LOADED"` 后,按模板预览按钮 SHALL 可点击"),涉及跨多个 spec 的 delta,且会改变下游 e2e 期望,应另起 change 单独处理

**替代方案**:
- 给 `extraction-preview-validation` 加一条 "按钮启用条件" 的 SHALL:超出本期最小化范围,且与本期"修复已有实现 bug"的目的不匹配(会让"修复 bug"变成"扩 spec")

### Decision 4: 截图中 `border` 从 `.screenshot` 转移到 `.screenshot-block`

**选择**:`.screenshot` 删除 `border: 1px solid #ddd;`;`.screenshot-block` 新增 `border: 1px solid #ddd;`。

**理由**:
- 容器需要边框(让滚动区域边界清晰),图片本身不需要
- 视觉等效(图片贴在容器内,边框在外),避免滚动时图片与边框出现错位
- 这是**伴随 Decision 1 的最小必要样式调整**,不引入新视觉风格

**替代方案**:
- 保留图片 border + 容器无 border:滚动时边框会随图片一起滚,体验不自然
- 给容器加 box-shadow 而非 border:超出"最小必要改动",且与现有 UI 风格(其他卡片用浅灰 border)不一致

## Risks / Trade-offs

- **[风险 1] 截图容器的 `max-height: 70vh` 在超长页面下需要"容器内滚动 + 页面外滚动"两层** → 已与用户确认接受此 UX(可视化造字段 Tab 内本就预期用户在固定区域工作)
- **[风险 2] `isLoaded` getter 的语义可能与其他已有代码冲突** → 搜索确认 `browserSessionStore.js` 现有 `state` 字段不会与 `'LOADED'` 字面值冲突;`getters` 字段当前不存在,无命名冲突
- **[风险 3] `currentUrl` 修复读取后无消费方,成为"读但不用"的字段** → 接受。这是顺手修掉的死代码读取,后续若有其他模块需要(如"页面已加载多久""最近加载 URL"日志)即可直接消费,不扩大本次范围
- **[风险 4] 新增 1 条测试对 `isPreviewDisabled === false` 的断言依赖 store getter 的实现细节** → 测试通过 `store.status = 'LOADED'` 设置状态、断言 `wrapper.vm.isPreviewDisabled === false`,只依赖 getter 公开行为,不依赖内部实现
- **[权衡 1] 不修 e2e 环境 blocker** → 已知 blocker 维持现状(spec 文档化),本次只保证前端单测与人工冒烟覆盖

## Migration Plan

无需 migration。本 change:
- 不改 schema、不改 DTO、不改 WebSocket 消息形状
- 不改任何公开 API
- 不需要灰度或回滚(纯前端 CSS/computed/store 调整,失败时回滚单个 commit 即可)
- 部署顺序:重新构建前端 → 浏览器刷新即可生效

**回滚策略**:如线上发现问题,`git revert` 本 change 的 commit。前端是无状态 UI 修复,后端无需任何动作。

## Open Questions

无。所有边界已通过 4 项决策锁死;关键 UX 决策(双向滚动 + 70vh + 不缩放图片)已与用户当面确认。
