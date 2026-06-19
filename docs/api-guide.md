# API 参考

> 基础路径 `/api/v1` · 统一响应包络 `ApiResponse<T>{code, data, message}` · HTTP 状态码 200

## 统一响应

成功与错误都通过 `ApiResponse` 包络返回，**HTTP 状态码一律 200**，业务错误通过 `code` 区分：

```json
// 成功
{ "code": 200, "data": { ... }, "message": "success" }

// 业务错误（HTTP 200，但 code 非 200）
{ "code": 404, "data": null, "message": "CrawlConfig not found: id=99" }
```

`code` 取值：
- `200` — 成功
- `400` — 业务参数错误
- `404` — 资源不存在
- `500` — 系统错误

## 配置管理

### GET /api/v1/configs

分页查询配置。

**Query**: `page` (默认 0), `size` (默认 20), `sort` (默认 `createdAt,DESC`)

**Response `data`**：Spring `Page<ConfigResponse>` 结构

```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "新闻爬虫A",
        "pageType": "LIST_DETAIL",
        "selectorType": "CSS",
        "status": "STOPPED",
        "createdAt": "2026-06-01T10:00:00Z",
        "updatedAt": "2026-06-01T10:00:00Z",
        "fields": []
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": "success"
}
```

### POST /api/v1/configs

创建配置。`name` / `startUrl` / `pageType` / `selectorType` 必填（`startUrl` M4 起强制）；`status` 不传时默认 `STOPPED`。`startUrl` 经 UrlGuard 校验：协议必须 `http(s)`，host 不能为 `localhost` / `127.0.0.1` / `::1`；违反返回 `code=4007`。

**Request body**：
```json
{
  "name": "新闻爬虫A",
  "startUrl": "https://example.com/list",
  "pageType": "LIST_DETAIL",
  "selectorType": "CSS"
}
```

**Response**：HTTP 201，body 为 `ApiResponse<ConfigResponse>`

**错误**：
- `code=4007, message="startUrl 不能为空"` — 缺失 `startUrl`
- `code=4007, message="目标地址被禁止访问（回环）"` — `startUrl` 指向回环
- `code=4007, message="URL 格式不合法"` — 协议非 http(s) 或非合法 URI

### GET /api/v1/configs/{id}

获取配置详情（包含 `fields` 列表，按 `createdAt ASC` 排序）。**M4 起响应含 `startUrl`**。

**Response `data`**：
```json
{
  "id": 1,
  "name": "新闻爬虫A",
  "startUrl": "https://example.com/list",
  "pageType": "LIST_DETAIL",
  "selectorType": "CSS",
  "status": "STOPPED",
  "createdAt": "2026-06-01T10:00:00Z",
  "updatedAt": "2026-06-01T10:00:00Z",
  "fields": [
    {
      "id": 1,
      "pageType": "LIST",
      "fieldName": "title",
      "fieldType": "TEXT",
      "selector": "h1.title",
      "createdAt": "2026-06-01T10:01:00Z",
      "updatedAt": "2026-06-01T10:01:00Z"
    }
  ]
}
```

**错误**：不存在 → `code=404, message="CrawlConfig not found: id=99"`

### PUT /api/v1/configs/{id}

更新配置 + 全量替换字段列表。**M4 起 PUT 也需传 `startUrl`**（与 POST 同样经 UrlGuard 校验，缺失或非法返回 `code=4007`）。

**Request body**：
```json
{
  "name": "新闻爬虫A (更新)",
  "startUrl": "https://example.com/list",
  "pageType": "LIST_DETAIL",
  "selectorType": "XPATH",
  "fields": [
    { "pageType": "LIST", "fieldName": "title", "fieldType": "TEXT", "selector": "//h1[@class='title']" },
    { "pageType": "DETAIL", "fieldName": "content", "fieldType": "TEXT", "selector": "//div[@class='content']" }
  ]
}
```

`fields` 为空数组则清空所有字段；不传 `fields` 保留现有字段；传新 `fields` 数组则**先清空旧字段，再插入新字段**（事务内原子操作）。

**Response `data`**：更新后的 `ConfigResponse`

### DELETE /api/v1/configs/{id}

删除配置，级联删除所有关联字段。

**Response**：HTTP 204 No Content

**错误**：不存在 → `code=404`

## 字段管理（作为配置子资源）

### GET /api/v1/configs/{configId}/fields

列出指定配置的所有字段，按 `createdAt ASC` 排序。

**Response `data`**：`FieldResponse[]`

**错误**：`configId` 不存在 → `code=404`

### POST /api/v1/configs/{configId}/fields

为指定配置添加字段。

**Request body**：
```json
{
  "pageType": "LIST",
  "fieldName": "title",
  "fieldType": "TEXT",
  "selector": "h1.title"
}
```

**Response**：HTTP 201，body 为 `ApiResponse<FieldResponse>`

**错误**：`configId` 不存在 → `code=404`

### PUT /api/v1/fields/{id}

更新字段（不修改 `configId` 关联）。

**Request body**：同 `POST /configs/{id}/fields`

**Response `data`**：更新后的 `FieldResponse`

**错误**：字段不存在 → `code=404`

### DELETE /api/v1/fields/{id}

删除字段。

**Response**：HTTP 204 No Content

**错误**：字段不存在 → `code=404`

## 健康检查

### GET /api/v1/health

**Response `data`**：
```json
{
  "status": "UP",
  "database": "UP",
  "timestamp": "2026-06-01T10:00:00Z"
}
```

`database` 字段：`UP` 表示连接池能成功获取有效连接，`DOWN` 表示失败。

## 枚举值参考

| 枚举 | 取值 |
|------|------|
| `PageType` | `LIST_DETAIL` / `DETAIL_ONLY` |
| `SelectorType` | `CSS` / `XPATH` |
| `FieldType` | `TEXT` / `NUMBER` / `DATE` / `URL` |
| `FieldPageType` | `LIST` / `DETAIL` |
| `ConfigStatus` | `ACTIVE` / `STOPPED`（创建时默认 `STOPPED`） |

## 错误码汇总

| code | HTTP | 场景 | 触发条件 |
|------|------|------|----------|
| 200  | 200 | 成功 | 所有正常请求 |
| 400  | 200 | 参数错误（M1） | configs/fields 既有接口的 DTO 校验失败 / 业务参数无效 |
| 404  | 200 | 资源不存在 | configId / id 不存在 |
| 500  | 200 | 系统错误 | 兜底异常 |
| 4001 | 400 | URL 不合法 | `/api/v1/page-fetch` 入参为空 / 非 http(s) 协议 / 非合法 URI |
| 4002 | 502 | 目标不可达 | DNS 解析失败 / TCP 连接拒绝 / 其它 IOException |
| 4003 | 403 | 目标地址被禁止访问 | URL host 为 `localhost` / `127.0.0.1` / `::1` |
| 4004 | 504 | 加载超时 | 抓取超过 `page-fetch.timeout`（默认 8s） |
| 4005 | 502 | 响应体过大 | 响应体超过 `page-fetch.max-size`（默认 2MB） |
| 4007 | 200 | startUrl 校验失败 | POST/PUT `/api/v1/configs` 时 `startUrl` 缺失 / 协议非 http(s) / 指向回环 |
| 4090 | 200 | 同时已有 RUNNING 任务 | POST `/api/v1/tasks` 时全局锁被占（CrawlEngine 进程内单任务锁） |

> **注意**：M1 既有接口（configs / fields）所有响应仍然 HTTP 200 + body code 区分；M2 引入的 `/api/v1/page-fetch` 走 HTTP 状态码 + body code 双层语义，方便前端按 HTTP 分类做粗粒度处理、按 code 做精确文案提示。

## 页面加载（M2 同步加载 MVP）

### POST /api/v1/page-fetch

同步抓取目标页面，返回标题、最终 URL、字节数等元信息。不渲染页面，不返回完整 HTML。

**Request**:

```json
{ "url": "https://example.com" }
```

**Response（成功）**:

```json
{
  "code": 200,
  "data": {
    "status": "SUCCESS",
    "finalUrl": "https://example.com",
    "title": "Example Domain",
    "contentLength": 1256,
    "fetchedAt": "2026-06-01T10:00:00Z"
  },
  "message": "success"
}
```

**Response（错误）**：HTTP 状态码与 `code` 字段见上表错误码汇总。

**配置项**（`application.yml`）：

```yaml
page-fetch:
  timeout: 8s        # 抓取总超时
  max-size: 2MB      # 响应体最大字节数（超出立即中止）
  user-agent: VisualSpider4/0.1
```

**安全限制（极简版）**：

- 协议白名单：仅接受 `http` / `https`
- 拒绝最常见回环目标：URL host 为 `localhost`（不区分大小写）/ 字面 IP `127.0.0.1` / `::1`
- 不做私网段扫描、不做 DNS 解析后再次校验（这些留待后续 SSRF 加固 change）

## 浏览器会话（可视化预览）

### POST /api/v1/browser/sessions

打开一个 Playwright 浏览器会话（单例）。同一时间最多 1 个活跃会话；重复打开返回 `409`。

**Response**：
```json
{ "code": 200, "data": { "sessionId": "uuid", "status": "ACTIVE", "currentUrl": null, "createdAt": "..." }, "message": "success" }
```

**错误**：
- `code=409, message="已有活跃会话，请先关闭"` — 已有活跃会话

### DELETE /api/v1/browser/sessions/{id}

关闭指定会话。

**Response**：`{ "code": 200, "data": null, "message": "success" }`

**错误**：
- `code=404` — sessionId 不存在或已关闭

### GET /api/v1/browser/sessions

查询当前会话状态。

**Response（有活跃）**：`{ "code": 200, "data": { "sessionId": "uuid", "status": "ACTIVE", "currentUrl": "https://...", "createdAt": "..." }, ... }`
**Response（无活跃）**：`{ "code": 200, "data": { "sessionId": null, "status": "CLOSED", "currentUrl": null, "createdAt": null }, ... }`

## WebSocket /api/v1/ws/page

浏览器会话建立后，前端通过 WebSocket 与后端交互，协议基于统一 `WsMessage` 信封：

```
{ "type": "<消息类型>", "payload": { ... } }
```

### 客户端 → 服务端

| type | payload | 行为 |
|------|---------|------|
| `load` | `{ url, configId }` | 加载 URL；首条消息携带 configId 用于后续 saveField |
| `click` | `{ x, y }` | 视口坐标点击；给命中元素加红框（`.vs-highlight`）后推 `screenshot`，并返回 `selectors` 消息 |
| `scroll` | `{ dy }` | 滚动 Playwright 页面（`window.scrollBy(0, dy)`，正向下/负向上）后重推视口截图；截图仍为 1280×800 视口，点击坐标始终对齐当前视口 |
| `preview` | `{ selectorType: "css"\|"xpath", selector }` | 注入高亮 + 推新截图 |
| `saveField` | `{ pageType, fieldName, fieldType, selector }` | 落库到 crawl_field（依赖 session 绑定的 configId） |
| `previewTemplate` | `{ pageType: "LIST"\|"DETAIL" }` | M3 按模板批量预览：对当前 Playwright Page 上指定 pageType 下所有 crawl_field 执行选择器 + 校验 |
| `close` | `null` | 关闭浏览器会话 |

### 服务端 → 客户端

| type | payload | 说明 |
|------|---------|------|
| `state` | `{ state, message }` | `LOADED` / `ERROR` / `CLOSED` |
| `screenshot` | `{ data: "<base64 png>" }` | 截图帧 |
| `selectors` | `{ css: { selector, matchCount, samples }, xpath: { ... }, nested }` | 点击返回的候选；`nested=true` 表示命中 iframe / shadow host，本期不深入选择，前端应提示 |
| `previewResult` | `{ matchCount, samples }` | preview 匹配数 |
| `saveFieldResult` | `{ ok, fieldId, message }` | saveField 落库结果 |
| `previewTemplateResult` | `{ result: { fields: [...], warnings: [...] } }` | M3 按模板预览结果，详见下文 |
| `error` | `{ code, message }` | 错误：`NO_ELEMENT` / `NO_SESSION` / `NAVIGATION_FAILED` / `ALREADY_ACTIVE` / `NOT_FOUND` / `BAD_REQUEST` / `BUSINESS` / `UNKNOWN` |

### M3: `previewTemplate` / `previewTemplateResult`

**客户端 → 服务端** (`previewTemplate`)：

```json
{ "type": "previewTemplate", "payload": { "pageType": "LIST" } }
```

约束：
- 必须**先**发送过 `load` 消息（绑定 configId）
- 当前 BrowserSession 必须有活跃 Page，否则服务端返回 `error.code=NO_SESSION`

**服务端 → 客户端** (`previewTemplateResult`)：

```json
{
  "type": "previewTemplateResult",
  "payload": {
    "result": {
      "fields": [
        {
          "fieldId": 1,
          "fieldName": "title",
          "fieldType": "TEXT",
          "selector": ".title",
          "matchCount": 3,
          "rawValues": ["Game 7 Preview", "Match Recap", "Injury Update"],
          "validatedValues": ["Game 7 Preview", "Match Recap", "Injury Update"],
          "status": "OK"
        },
        {
          "fieldId": 2,
          "fieldName": "price",
          "fieldType": "NUMBER",
          "selector": ".price",
          "matchCount": 3,
          "rawValues": ["99", "199", "49"],
          "validatedValues": ["99", "199", "49"],
          "status": "OK"
        },
        {
          "fieldId": 3,
          "fieldName": "dateInvalid",
          "fieldType": "DATE",
          "selector": ".title",
          "matchCount": 3,
          "rawValues": ["Game 7 Preview", "Match Recap", "Injury Update"],
          "validatedValues": [null, null, null],
          "status": "TYPE_MISMATCH",
          "message": "非 ISO 8601 日期: \"Game 7 Preview\""
        },
        {
          "fieldId": 4,
          "fieldName": "missing",
          "fieldType": "TEXT",
          "selector": ".no-such",
          "matchCount": 0,
          "rawValues": [],
          "validatedValues": [],
          "status": "NO_MATCH"
        },
        {
          "fieldId": 5,
          "fieldName": "broken",
          "fieldType": "TEXT",
          "selector": ">>>broken<<<",
          "matchCount": 0,
          "rawValues": [],
          "validatedValues": [],
          "status": "SELECTOR_INVALID",
          "message": "SyntaxError: ..."
        }
      ],
      "warnings": [
        "LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截"
      ]
    }
  }
}
```

`status` 字段级四态：

| 状态 | 触发条件 | `rawValues` | `validatedValues` |
|------|---------|-------------|--------------------|
| `OK` | 命中 ≥1 + 全部校验通过 | 原始字符串数组 | 与 raw 等长且值合法 |
| `TYPE_MISMATCH` | 命中 ≥1 + 至少 1 个校验失败 | 原始字符串数组 | 失败位置为 `null` |
| `NO_MATCH` | 命中 0 | `[]` | `[]` |
| `SELECTOR_INVALID` | Page.evaluate 抛错 | `[]` | `[]` + `message` 含错误 |

`warnings` 软警告（非阻塞）：

- `"该模板未定义任何 <pageType> 字段"` — 该 pageType 下零字段
- `"LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截"` — LIST_DETAIL + LIST 预览且缺 `field_name="detail_url"` + `field_type=URL` 字段

**类型校验细则**（`FieldValueValidator`）：
- `TEXT`: trim 后非空 → 合法；空串 → `null`（视为 NO_MATCH 等价）
- `NUMBER`: `Double.parseDouble` 不抛 → 合法；**不接受千分位**（`"1,234"` 非法）
- `DATE`: `DateTimeFormatter.ISO_DATE` 或 `ISO_DATE_TIME` 能解析 → 合法；**严格 ISO 8601**（`"2026/06/12"` / `"Jun 12, 2026"` 非法）
- `URL`: `new URI(raw).isAbsolute()` 且 scheme 为 `http`/`https` → 合法；其他（`mailto:` / `ftp://` / 相对路径）非法

**URL 字段抽取策略**：在 `field_type=URL` 时优先读取元素 DOM 的 `.href`（浏览器自动绝对化相对路径）；元素无 `.href` 时退回 `textContent.trim()`。其他类型一律读 `textContent.trim()`。

## curl 示例

```bash
# 创建配置
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"name":"新闻爬虫","pageType":"LIST_DETAIL","selectorType":"CSS"}'

# 分页查询
curl 'http://localhost:8080/api/v1/configs?page=0&size=10'

# 更新配置 + 替换字段
curl -X PUT http://localhost:8080/api/v1/configs/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"新闻爬虫","pageType":"LIST_DETAIL","selectorType":"CSS","fields":[]}'

# 删除
curl -X DELETE http://localhost:8080/api/v1/configs/1

# 页面加载（M2）
curl -X POST http://localhost:8080/api/v1/page-fetch \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'

# 危险地址（应返回 403 + code=4003）
curl -i -X POST http://localhost:8080/api/v1/page-fetch \
  -H "Content-Type: application/json" \
  -d '{"url":"http://localhost"}'

# 打开浏览器会话
curl -X POST http://localhost:8080/api/v1/browser/sessions

# 查询会话状态
curl http://localhost:8080/api/v1/browser/sessions

# 关闭浏览器会话
curl -X DELETE http://localhost:8080/api/v1/browser/sessions/<sessionId>

# 创建爬取任务（DETAIL_ONLY,带 URLs）
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"configId":3,"urls":["https://example.com/article/1","https://example.com/article/2"]}'

# 创建爬取任务（LIST_DETAIL）
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"configId":1,"urls":null}'

# 任务列表
curl 'http://localhost:8080/api/v1/tasks?page=0&size=20'

# 任务详情
curl http://localhost:8080/api/v1/tasks/1

# 优雅停止任务
curl -X POST http://localhost:8080/api/v1/tasks/1/stop

# 删除任务（级联清理爬取产物）
curl -X DELETE http://localhost:8080/api/v1/tasks/1

# 按任务查 article 列表
curl 'http://localhost:8080/api/v1/articles?task_id=1&page=0&size=20'

# 按 config 查 article 列表（带关键词）
curl 'http://localhost:8080/api/v1/articles?config_id=1&keyword=warrior&page=0&size=20'

# 文章详情
curl http://localhost:8080/api/v1/articles/1

# 导出 JSON
curl -X POST 'http://localhost:8080/api/v1/articles/export?format=JSON&config_id=1' \
  -H "Content-Type: application/json" --data '{}'
```

## 爬取任务（M4）

### POST /api/v1/tasks

创建爬取任务。`configId` 必填；`urls` 行为按 `pageType` 区分：

- **DETAIL_ONLY**：`urls` 必填且非空数组。系统为每个 URL 创建一条 `detail_url`（PENDING），任务启动后 `CrawlEngine.runDetailOnly` 逐个 `page.goto` + 抽取。
- **LIST_DETAIL**：`urls` 传 `null`。系统从 `config.startUrl` 解析列表页，抽取列表项后逐项 `page.goto(detail_url)`。

**Request body**：

```json
{ "configId": 3, "urls": ["https://example.com/article/1"] }
```

```json
{ "configId": 1, "urls": null }
```

**Response `data`**：HTTP 201，`TaskResponse`

```json
{
  "id": 1,
  "configId": 3,
  "pageType": "DETAIL_ONLY",
  "status": "RUNNING",
  "totalItems": 1,
  "crawledItems": 0,
  "failedItems": 0,
  "startedAt": "2026-06-17T14:38:51Z",
  "completedAt": null,
  "errorMessage": null
}
```

**错误**：
- `code=404` — `configId` 不存在
- `code=4090, message="已有任务在运行"` — 全局已有 1 个 RUNNING 任务（CrawlEngine 进程内单任务锁，新任务立即拒绝）
- `code=400` — DETAIL_ONLY 时 `urls` 为 null 或空数组

### GET /api/v1/tasks

分页任务列表，按 `started_at DESC` 排序。

**Query**：`config_id`（可选，全局列表时不传）、`page`（默认 0）、`size`（默认 20）

**Response `data`**：Spring `Page<TaskResponse>`

### GET /api/v1/tasks/{id}

**Response `data`**：`TaskResponse`（字段同 POST 响应）

**错误**：`code=404` — taskId 不存在

### POST /api/v1/tasks/{id}/stop

优雅停止：设置 `CrawlEngine.stopFlag`，引擎在处理下一个 detail_url / list_item 之前检查并退出。任务状态仍为 `COMPLETED`（非 FAILED），`crawledItems + failedItems` 可能 < `totalItems`。

**Response**：HTTP 200，`data: null`

**错误**：
- `code=404` — taskId 不存在或未在运行

### DELETE /api/v1/tasks/{id}

级联删除该任务下的全部 list_page / list_item / article / detail_url。删除完成返回 204 No Content。

**Response**：HTTP 204

## 爬取条目（M4）

### GET /api/v1/articles

分页查询爬取条目。**过滤优先级**：`task_id` > `config_id` > 全部。

**Query**：
- `task_id`（可选，精确过滤该任务的 article）
- `config_id`（可选，task_id 缺省时按 config 过滤）
- `keyword`（可选，在 `custom_fields` JSON 文本上做 LIKE，大小写敏感）
- `page`（默认 0）、`size`（默认 20）

**Response `data`**：Spring `Page<ArticleSummary>`

```json
{
  "id": 1,
  "configId": 3,
  "url": "https://example.com/article/1",
  "status": "CRAWLED",
  "customFields": { "title": "示例标题" },
  "errorMessage": null,
  "fetchedAt": "2026-06-17T14:38:56Z"
}
```

### GET /api/v1/articles/{id}

**Response `data`**：`ArticleDetail`（在 summary 基础上含 `raw_html` 完整内容）

**错误**：`code=404` — articleId 不存在

### POST /api/v1/articles/export?format=JSON|xlsx

按当前过滤条件导出。**Query**：`format`（`JSON` / `xlsx`，必填）、`config_id`（可选）、`keyword`（可选）。`task_id` 暂不支持导出过滤。

**列集合**：聚合当前过滤结果中所有 article 的 `custom_fields` 键的并集。JSON 导出缺失列填 `null`；xlsx 导出缺失列填空字符串。

**Response**：
- `format=JSON` → `application/json`，body 为 `Array<{id, url, status, fetched_at, ...customFields}>`（顺序与分页查询一致）
- `format=xlsx` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，body 为 xlsx 二进制

