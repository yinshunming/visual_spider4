# M1 项目管理里程碑计划

> 创建日期: 2026-05-31
> 状态: 已确认，待执行

---

## 目标

完成项目管理完整闭环（后端API + 前端页面 + Testcontainers测试）

---

## 技术栈

| 分层 | 技术 |
|------|------|
| 前端框架 | Vue 3 + Vite |
| UI 库 | Element Plus |
| 状态管理 | Pinia |
| HTTP 客户端 | Axios |
| 路由 | vue-router |
| 后端框架 | Spring Boot 3.2.5 |
| ORM | JPA + Spring Data |
| 数据库 | PostgreSQL |
| 测试 | JUnit 5 + Mockito + MockMvc + Testcontainers |

---

## 范围边界

### 包含

- 配置 CRUD（name, pageType, selectorType, status）
- 字段 CRUD（fieldName, fieldType, selector, pageType）
- 字段作为配置的子资源（方案A）
- 更新配置时字段全量替换
- 列表查询（无条件过滤）
- 前端页面（/configs, /configs/new, /configs/:id）
- 完整测试（Repository/Service/Controller层）

### 不包含

- 任务管理（crawl_task）
- 数据爬取
- 数据导出
- 字段排序功能
- 高级搜索/过滤

---

## 开发顺序

```
阶段1: 后端基础层
    ├── 1.1 Entity (CrawlConfig, CrawlField)
    ├── 1.2 Repository
    └── 1.3 Repository 测试 (Testcontainers)
    │
    ▼
阶段2: 后端业务层
    ├── 2.1 CrawlConfigService + 测试
    ├── 2.2 CrawlFieldService + 测试
    └── 2.3 业务层集成测试
    │
    ▼
阶段3: 后端 API 层
    ├── 3.1 ConfigController + 测试
    ├── 3.2 FieldController + 测试
    └── 3.3 API 端到端测试
    │
    ▼
阶段4: 前端基础层
    ├── 4.1 安装 vue-router
    ├── 4.2 config API 模块
    ├── 4.3 useConfigStore
    └── 4.4 路由配置
    │
    ▼
阶段5: 前端页面
    ├── 5.1 ConfigList.vue
    └── 5.2 ConfigEdit.vue
    │
    ▼
阶段6: 联调验证
```

---

## 文件结构

### 后端

```
backend/src/main/java/com/visualspider/
├── entity/
│   ├── CrawlConfig.java
│   └── CrawlField.java
├── repository/
│   ├── CrawlConfigRepository.java
│   └── CrawlFieldRepository.java
├── service/
│   ├── CrawlConfigService.java
│   └── CrawlFieldService.java
├── controller/
│   ├── ConfigController.java
│   └── FieldController.java
├── dto/
│   ├── request/
│   │   ├── CreateConfigRequest.java
│   │   ├── UpdateConfigRequest.java
│   │   └── CreateFieldRequest.java
│   └── response/
│       ├── ConfigResponse.java
│       └── FieldResponse.java
└── exception/
    ├── ConfigNotFoundException.java
    └── ValidationException.java

backend/src/test/java/com/visualspider/
├── repository/
│   └── CrawlConfigRepositoryTest.java (Testcontainers)
├── service/
│   ├── CrawlConfigServiceTest.java
│   └── CrawlFieldServiceTest.java
└── controller/
    ├── ConfigControllerTest.java
    └── FieldControllerTest.java
```

### 前端

```
frontend/src/
├── api/config.js
├── stores/configStore.js
├── views/
│   ├── ConfigList.vue
│   └── ConfigEdit.vue
├── router/index.js
└── App.vue (更新路由)
```

---

## API 设计

| 方法 | 路径 | 说明 |
|-----|------|-----|
| POST | /configs | 创建配置 |
| GET | /configs | 分页查询（无条件过滤） |
| GET | /configs/:id | 获取配置详情（含字段） |
| PUT | /configs/:id | 更新配置（全量替换字段） |
| DELETE | /configs/:id | 删除配置（级联删除字段） |
| GET | /configs/:id/fields | 获取字段列表 |
| POST | /configs/:id/fields | 添加字段 |
| PUT | /fields/:id | 更新字段 |
| DELETE | /fields/:id | 删除字段 |

---

## 数据模型

### CrawlConfig

| 字段 | 类型 | 约束 |
|------|------|------|
| id | Long | auto, PK |
| name | String | not null |
| pageType | Enum | LIST_DETAIL, DETAIL_ONLY |
| selectorType | Enum | CSS, XPATH |
| status | Enum | ACTIVE, STOPPED (默认 STOPPED) |
| createdAt | Timestamp | auto |
| updatedAt | Timestamp | auto |

### CrawlField

| 字段 | 类型 | 约束 |
|------|------|------|
| id | Long | auto, PK |
| configId | Long | FK to CrawlConfig |
| pageType | Enum | LIST, DETAIL |
| fieldName | String | not null |
| fieldType | Enum | TEXT, NUMBER, DATE, URL |
| selector | String | not null |
| createdAt | Timestamp | auto |
| updatedAt | Timestamp | auto |

---

## 校验规则

1. **detail_url 必填**（LIST_DETAIL 模式）
   - 必须在 page_type = LIST 中存在 field_name = 'detail_url', field_type = 'URL' 的字段
   - 否则阻止启动爬取任务

2. **字段全量替换**
   - PUT /configs/:id 时，body 中的 fields[] 会整体替换现有字段

---

## 测试策略

```
测试金字塔:
         ┌─────────┐
         │   E2E   │  ← 手动联调验证
         ├─────────┤
         │   API   │  ← @WebMvcTest ConfigController, FieldController
         ├─────────┤
         │ Service │  ← Mockito Service Tests
         ├─────────┤
         │   Repo  │  ← Testcontainers + PostgreSQL
         └─────────┘
```

---

## 前端路由

| 路径 | 页面 | 说明 |
|------|------|------|
| /configs | ConfigList | 配置列表页 |
| /configs/new | ConfigEdit | 新建配置 |
| /configs/:id | ConfigEdit | 编辑配置（详情合一） |

---

## 确认记录

- [x] M1 包含字段管理
- [x] M1 包含前端页面
- [x] 使用 Testcontainers 做集成测试
- [x] 字段管理采用方案A（子资源）
- [x] 新建页面: /configs/new
- [x] 详情页和编辑页合一
- [x] 不需要字段排序
- [x] 不支持列表搜索/过滤
- [x] 更新配置时字段全量替换
- [x] 默认状态: STOPPED
- [x] STOPPED 状态下可以编辑
- [x] STOPPED 状态下可以删除
