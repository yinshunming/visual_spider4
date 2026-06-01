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

| code | 场景 | 触发条件 |
|------|------|----------|
| 200 | 成功 | 所有正常请求 |
| 400 | 参数错误 | DTO 校验失败 / 业务参数无效 |
| 404 | 资源不存在 | configId / id 不存在 |
| 500 | 系统错误 | 兜底异常 |

> 当前没有 401/403，**MVP 阶段不实现认证鉴权**（参见 [openspec/specs/system-boundaries/spec.md](../openspec/specs/system-boundaries/spec.md)）。

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
```
