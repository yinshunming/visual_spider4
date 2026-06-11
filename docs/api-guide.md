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

创建配置。`status` 不传时默认 `STOPPED`。

**Request body**：
```json
{
  "name": "新闻爬虫A",
  "pageType": "LIST_DETAIL",
  "selectorType": "CSS"
}
```

**Response**：HTTP 201，body 为 `ApiResponse<ConfigResponse>`

### GET /api/v1/configs/{id}

获取配置详情（包含 `fields` 列表，按 `createdAt ASC` 排序）。

**Response `data`**：
```json
{
  "id": 1,
  "name": "新闻爬虫A",
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

更新配置 + 全量替换字段列表。

**Request body**：
```json
{
  "name": "新闻爬虫A (更新)",
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
| `click` | `{ x, y }` | 视口坐标点击；返回 `selectors` 消息 |
| `preview` | `{ selectorType: "css"\|"xpath", selector }` | 注入高亮 + 推新截图 |
| `saveField` | `{ pageType, fieldName, fieldType, selector }` | 落库到 crawl_field（依赖 session 绑定的 configId） |
| `close` | `null` | 关闭浏览器会话 |

### 服务端 → 客户端

| type | payload | 说明 |
|------|---------|------|
| `state` | `{ state, message }` | `LOADED` / `ERROR` / `CLOSED` |
| `screenshot` | `{ data: "<base64 png>" }` | 截图帧 |
| `selectors` | `{ css: { selector, matchCount, samples }, xpath: { ... } }` | 点击返回的候选 |
| `previewResult` | `{ matchCount, samples }` | preview 匹配数 |
| `saveFieldResult` | `{ ok, fieldId, message }` | saveField 落库结果 |
| `error` | `{ code, message }` | 错误：`NO_ELEMENT` / `NO_SESSION` / `NAVIGATION_FAILED` / `ALREADY_ACTIVE` / `NOT_FOUND` / `BAD_REQUEST` / `BUSINESS` / `UNKNOWN` |

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
```
