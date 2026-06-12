# E2E 测试

真 Chromium 浏览器跑 `PagePreview.vue` 全链路：URL 加载 → 截图 → 点击 → CSS 候选 → 预览高亮 → 字段保存 → DB 断言。

## 前置

按顺序完成：

1. **起 PostgreSQL**（开发者本机服务）
   ```powershell
   pg_isready -h localhost -p 5432   # 验证
   ```
   详见 [../docs/runbook.md §PostgreSQL](../docs/runbook.md)。

2. **打后端 jar**（脚本会校验路径）
   ```bash
   cd backend
   mvn -o clean package -DskipTests
   ```

3. **装 Playwright 后端 Chromium**（截图/点击需要）
   ```bash
   cd backend
   mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
   ```

4. **装 E2E 依赖 + 浏览器**（E2E 用的是前端测试库，需要单独的 Chromium）
   ```bash
   cd e2e
   npm install
   npm run install-browser
   ```

## 跑测试

```bash
cd e2e
npm test
```

`npm test` 会自动：
1. 启后端 jar（端口 8080）→ 等 `/api/v1/health` 通
2. 启前端 Vite dev（端口 5173）→ 等通
3. 跑 `tests/page-preview.spec.js`（真 Chromium）
4. 关掉所有进程

## 看日志

- `e2e/logs/backend.log` — 后端启动 + Playwright banner + 业务日志
- `e2e/logs/frontend.log` — Vite dev 输出
- `e2e/test-results/01-loaded.png` 等 — 测试步骤截图（仅失败时保留）
- `e2e/test-results/<test>-chromium/test-failed-1.png` — Playwright 自动截图
- `e2e/test-results/<test>-chromium/trace.zip` — Playwright trace（用 `npx playwright show-trace <zip>` 打开）
- `e2e/playwright-report/index.html` — Playwright HTML 报告（用 `npx playwright show-report` 打开）

> 上述目录都已加入 `.gitignore`（`e2e/test-results/` / `e2e/logs/` / `e2e/playwright-report/` / `e2e/run.log` / `e2e/run.pid`），不会进版本库。

## 写测试做了什么

1. **HTTP** 调后端创建/复用一条名为 `E2E 临时` 的 CrawlConfig（id=2 在本机 PG），记录字段数 baseline
2. **浏览器** 打开 `/configs/{id}/preview` → 自动 WS 握手 + openSession
3. 填 URL `https://example.com` → 点加载 → 等 `img.screenshot` 出现
4. 截图坐标 (320, 220) 处点击 → 后端 Playwright `elementFromPoint` → `tagPath=html > body > div`（命中 example.com 内容容器）→ 推 `selectors` 消息
5. 读 CSS selector 文本 → 点"预览匹配" → 高亮 + 推新截图 + `previewResult` 消息
6. 填 fieldName="e2e_title" → 字段类型=TEXT → 页面类型=DETAIL（默认值） → 点保存 → `.ok-hint`
7. **HTTP** 重新拉 `crawl_field` 列表，断言新增一条 `e2e_title`、pageType=DETAIL

## 已知问题

- example.com 页面改版会导致点击坐标失效。当前固定点 (320, 220) 命中 `html > body > div`；如改点 `<h1>` 需校准坐标到 (490, 290) 附近。
- 重复跑会留多个 `e2e_title` 字段（脚本不复用之前的），如需清理：
  ```sql
  DELETE FROM crawl_field WHERE field_name LIKE 'e2e_%';
  DELETE FROM crawl_config WHERE name = 'E2E 临时';
  ```
- 后端 jar 启动到 ws 握手 13-15s（Playwright 冷启），`start-stack.js` timeout 60s。
- 如果后端 Playwright banner（Chromium 没装），前端打开会报"Playwright 未就绪"，后续 WS 消息变成 `error`。
- `e2e/test-results/01-loaded.png` 等命名截图**只在测试失败时保留**（Playwright 默认行为）；成功路径下只留 `test-finished-1.png`。要看分步截图请去掉 `outputDir` 清理。
- 后端跨进程 Bash 工具跑 `npm test` 偶尔会被主动 kill（Playwright spawn Chrome 触发），后台跑法见 [docs/runbook.md §Known Issues 2](../docs/runbook.md)。
- 多次跑前先 `Get-Process -Name java,chrome,node | Stop-Process -Force`，否则上一个 jar 还占着 8080 端口。

## 端到端踩坑（开发本测试时遇到并修掉）

| 现象 | 根因 | 修复 |
|------|------|------|
| POST `/api/v1/configs` 报 "Required request body is missing" | Playwright `request.post(body)` 未设 Content-Type | 改 `request.post({ data, headers: { 'Content-Type': 'application/json' } })` |
| 后端 Playwright `page.evaluate` 抛 "Unsupported type of argument: [I@xxx" | Java `int[]` 不能当 JS 数组 | 改 `Map.of("x",..., "y",...)` |
| Jackson 反序列化 `selectorType: "css"` 抛 IllegalArgumentException | Jackson 默认 case-sensitive | `ObjectMapper.configure(ACCEPT_CASE_INSENSITIVE_ENUMS, true)` |
| 重复 openSession 触发 409 | 客户端 `onLoad` 重复 openSession | store 加 idempotency guard |
| WS URL 跨端口不稳 | dev Vite proxy 不会自动升级 WebSocket | dev 环境直连 8080 `ws://localhost:8080/api/v1/ws/page` |
| 测试把 `e2e_title` 填进了 URL 输入框 | locator `'input'` 第一个匹配 URL | 加 `.field-form-block` scope |
| `findElementByOuterHtml` 字符串匹配失败 | Jsoup 与浏览器 HTML 序列化差异 | 改用 `tagPath` 路径（tag + nth-of-type）匹配 |

## 不做的事

- ❌ GitHub Actions CI
- ❌ 多浏览器（Firefox/Safari）
- ❌ 自动起 PG
- ❌ 失败重试
- ❌ 并行
