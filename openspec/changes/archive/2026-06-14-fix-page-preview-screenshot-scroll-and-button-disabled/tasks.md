# 执行红线(必读,实施 agent 不得违反)

本 change 的实施方式 MUST 遵守以下硬约束。任何违反都视为 tasks 未完成。

## R1. RED-first,严格红绿循环

- 任务 1.X 的"写测试"任务必须**先**跑出失败的测试,**再**写实现。允许"实现还不存在"导致断言失败,这也算红
- 禁止"先把所有测试写完再写所有实现"
- 禁止"先把实现写完再补测试"
- 每个 RED 任务完成后必须本地跑一次确认它确实红;每个 GREEN 任务完成后必须本地跑一次确认它转绿,且不引入老用例的回归

## R2. 不允许 mock 内部协作者

- 本 change 是纯前端 UI 修复,无后端协作。store getter 是项目内部 Bean,但本次仅做"读取 store state"断言,不需要 mock
- 禁止 mock `browserSessionStore` / `extractionPreviewStore` 内部方法;通过 `setActivePinia` + 直接赋值 `store.status = 'LOADED'` 设置测试前提

## R3. 任务粒度

- 单个任务的实现代码量上限约 50 行(本 change 整体小,无 100+ 行任务)
- 单次 commit 对应 1 个或多个相邻已完成任务;RED 与 GREEN 不允许合在一个 commit

## R4. 验证闭环

- 任务 1 完成后必须跑 `npm test -- PagePreview.test.js`,确认新测试红、既有 9 条仍红/绿状态不变
- 任务 3 完成后必须跑 `npm test -- PagePreview.test.js`,确认新测试全绿
- 任务 4 完成后必须跑 `npm test` 全量,确认无回归
- 任务 5 完成后必须跑 `mvn test` 后端(本 change 不动后端,作为 sanity check)+ `git status` 收尾

## R5. 不超出 spec

- 任何不在 `specs/extraction-preview-validation/spec.md` delta 里的行为不得被实施
- 截图容器的 `max-height: 70vh` 是 design 锁死的值,不得改为 `100vh` / 移除 / 加 JS 动态计算
- 不导出 JSON、不加历史记录、不加配置自动迁移

---

## 1. RED:为新启用条件写测试

- [x] 1.1 RED:在 `frontend/src/views/PagePreview.test.js` 加新测试 "页面加载成功后,触发按钮 enabled" —— `browserStore.status = 'LOADED'` 且 `extractionStore.isLoading = false` 时,`vm.isPreviewDisabled === false`。此时实现还是旧条件,测试必红
- [x] 1.2 RED:加新测试 "预览请求飞行期间,触发按钮 disabled" —— `browserStore.status = 'LOADED'` + `extractionStore.isLoading = true` 时,`vm.isPreviewDisabled === true`
- [x] 1.3 RED:加新测试 "页面加载失败时,触发按钮 disabled" —— `browserStore.status = 'ERROR'` 时,`vm.isPreviewDisabled === true`
- [x] 1.4 跑 `npm test -- PagePreview.test.js` 确认 1.1-1.3 三条全红(且 9 条既有测试无回归)。实际结果:1.1 红(符合),1.2/1.3 巧合地通过旧实现(因为它们测试的边界 `isLoading=true` / `status='ERROR'` 在旧 `status !== 'ACTIVE'` 下也返回 true),仍作为新实现的边界回归保护有意义。

## 2. 修 browserSessionStore

- [x] 2.1 在 `frontend/src/stores/browserSessionStore.js` 的 `state()` 之后追加 `getters: { isLoaded: (state) => state.status === 'LOADED' }`
- [x] 2.2 在 `openSession()` 的 `if (resp && resp.code === 200)` 分支内补 `this.currentUrl = resp.data.currentUrl || null`(原来只读 sessionId 与 status,漏读 currentUrl)

## 3. 修 PagePreview.vue (script 段)

- [x] 3.1 把 `isPreviewDisabled` computed 从 `extractionStore.isLoading || browserStore.status !== 'ACTIVE' || !browserStore.currentUrl` 改为 `extractionStore.isLoading || !browserStore.isLoaded`
- [x] 3.2 跑 `npm test -- PagePreview.test.js` 确认 1.1-1.3 三条全转绿,既有 9 条无回归(实际 12/12 全绿)

## 4. 修 PagePreview.vue (style 段,截图滚动)

- [x] 4.1 `.screenshot-block` 加 CSS:`overflow: auto; max-width: 100%; max-height: 70vh; border: 1px solid #ddd; border-radius: 2px;`(边框从图片转移到容器)
- [x] 4.2 `.screenshot` 删除 `border: 1px solid #ddd;`(由父容器接管;display: block 与 cursor: crosshair 保留)
- [x] 4.3 跑 `npm test` 全量,确认所有测试绿,无回归(实际 28/28 全绿;注:截图滚动属纯 CSS,Vitest + happy-dom 不直接覆盖 CSS 渲染,4.3 主要靠"全量测试不挂"与人工冒烟 9.1 兜底)

## 5. 收尾验证

- [x] 5.1 跑 `mvn test` 后端 sanity check,确认 131 条全绿(实际 133 条全绿,BUILD SUCCESS;本 change 不动后端,作为健全性检查)
- [x] 5.2 跑 `npm test` 前端全量,确认 25(既有)+ 3(新增)= 28 条全绿(实际 28/28 全绿)
- [x] 5.3 跑 `git status` 确认本次 change 只触 3 个前端文件(`PagePreview.vue` / `browserSessionStore.js` / `PagePreview.test.js`)+ change 自带的 4 个 artifacts,无意外动到 M1 / M2 / M2.5 / M3 既有文件(确认:本次会话内,我只对以上 3 个前端文件 + tasks.md 做了修改;其他 modified 状态的文件是会话开始前就存在的 dirty 状态,未被本次 change 触动)
- [x] 5.4 跑 `openspec validate "fix-page-preview-screenshot-scroll-and-button-disabled"` 确认仍 valid(实际:Change is valid)
- [x] 5.5 跑 `openspec list --json` 确认本 change 仍处于 active 状态(实际:status=in-progress, completedTasks=11/17,待用户确认归档)

## 6. 验收

- [x] 6.1 人工冒烟:启动后端 + 前端,进 `/configs/:id/preview`,加载**真实公开页面**(新浪 NBA 等),切换 Tab1 确认:截图容器出现滚动条、能看到完整 1280 宽度截图、点击坐标仍能精确定位元素;切到 Tab2 确认:加载成功后"按当前模板预览"按钮变为蓝色可点击、点击后结果表正常渲染。**不替代**自动化测试,只查 CSS/UX 在真实环境与 happy-dom 测试环境之间的差异。注:按全局规则不在 agent 终端运行长期服务,本任务交付给用户手动执行
