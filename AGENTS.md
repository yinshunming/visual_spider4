# AGENTS.md - 可视化爬虫 MVP

**项目**: visual_spider4
**当前里程碑**: M1（项目管理）已完成；M2+ 待规划
**技术栈**: Vue3 / Vite / Element Plus / Pinia / vue-router + Spring Boot 3.2.5 / JPA / PostgreSQL 16 / Maven / Java 21

---

## 0. 必读红线（先看这里）

- **包名** `com.visualspider`，所有 Java 类必须放此包下
- **统一响应** `ApiResponse<T>{code, data, message}` 包络所有 controller 返回，错误也用同一包络
- **删除走 `service.delete(entity)`，不是 `repository.deleteById(id)`** — 后者跳过 JPA cascade，导致外键违反
- **JPA 双向上**：`CrawlConfig` 的 `fields` 集合和 `CrawlField.config` 引用必须同时维护，单边修改 cascade 不触发
- **测试用本机 PG**（开发者手工启动），不是 Testcontainers — 见 [docs/runbook.md](docs/runbook.md) §PostgreSQL；未启动时后端启动日志会打印多行 banner 提示，agent 见到后会主动告知用户手工启动
- **不写实现后再补测试**，写测试 → 失败 → 写实现 → 通过（RED→GREEN）

## 1. 仓库结构

```
visual_spider4/
├── backend/                          # Spring Boot 3.2.5 + JPA + Java 21
│   ├── src/main/java/com/visualspider/
│   │   ├── Application.java
│   │   ├── controller/               # REST 控制器（Config/Field/Health）
│   │   ├── service/                  # 业务层（CrawlConfigService / CrawlFieldService）
│   │   ├── repository/               # JPA 仓库
│   │   ├── entity/                   # JPA 实体（CrawlConfig, CrawlField）
│   │   ├── dto/
│   │   │   ├── request/              # CreateConfigRequest / CreateFieldRequest / UpdateConfigRequest
│   │   │   └── response/             # ConfigResponse / FieldResponse
│   │   ├── enums/                    # PageType / SelectorType / FieldType / ConfigStatus / FieldPageType
│   │   └── exception/                # BusinessException / GlobalExceptionHandler / ConfigNotFoundException
│   ├── src/test/                     # 37 个测试（Repository/Service/Controller）
│   ├── src/main/resources/application.yml
│   ├── src/test/resources/application-test.yml
│   └── pom.xml
├── frontend/                         # Vue 3 + Vite + Element Plus + Pinia
│   └── src/
│       ├── api/                      # config.js（Axios 封装）
│       ├── stores/                   # configStore.js（Pinia）
│       ├── views/                    # ConfigList.vue / ConfigEdit.vue
│       ├── router/index.js
│       ├── App.vue / main.js
│       └── vitest.config.js
├── openspec/
│   ├── specs/                        # 9 个能力真相源（开发依据）
│   └── changes/
│       ├── m1-project-management/    # 当前活跃 change（4/4 artifacts 完成）
│       └── archive/                  # 已归档
├── docs/                             # 深入文档（架构、API、运维、TDD）
├── AGENTS.md                         # 本文件
└── README.md                         # 入门与运行
```

## 2. 开发命令速查

```bash
# 后端
cd backend
mvn test                              # 跑所有测试（37 项）
mvn spring-boot:run                   # 启动服务（端口 8080）

# 前端
cd frontend
npm install
npm run dev                           # 启动 Vite（端口 5173，已配代理 /api -> 8080）
npm run build                         # 生产构建
npm test                              # vitest（前端测试，本里程碑未写）

# 数据库
# 启动本机 PostgreSQL 服务（详见 docs/runbook.md §PostgreSQL）
pg_isready -h localhost -p 5432       # 验证 PG 可达
```

**环境变量**（后端）：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`，均有默认值，详见 `application.yml`。

## 3. 当前里程碑状态

| Capability | Spec | 实现 | 状态 |
|-----------|------|------|------|
| `project-management` | ✅ | ✅ | M1 完成 — 37 测试通过 |
| `page-visual-selection` | ✅ | ⬜ | 未开始 |
| `selector-rule-management` | ✅ | ⬜ | 未开始 |
| `extraction-template` | ✅ | ⬜ | 未开始 |
| `extraction-preview-validation` | ✅ | ⬜ | 未开始 |
| `crawl-execution` | ✅ | ⬜ | 未开始 |
| `data-persistence` | ✅ | ⬜ | 未开始 |
| `system-boundaries` | ✅ | ⬜ | 未开始 |
| `dev-environment` | ✅ | ✅ | M0 完成 |

**M1 范围**：配置 CRUD + 字段 CRUD（作为配置子资源）+ 全量更新字段替换 + 前端列表/编辑页。

## 4. 路由速查

```
后端 REST API（全部 /api/v1 前缀）：

GET    /configs                  分页查询配置
POST   /configs                  创建配置（status 默认 STOPPED）
GET    /configs/{id}             获取配置详情（带字段）
PUT    /configs/{id}             更新配置（fields[] 全量替换）
DELETE /configs/{id}             删除配置（级联删除字段）

GET    /configs/{id}/fields      获取配置的所有字段
POST   /configs/{id}/fields      添加字段
PUT    /fields/{id}              更新字段
DELETE /fields/{id}              删除字段

GET    /health                   健康检查

前端路由：
/                            -> /configs 重定向
/configs                     ConfigList（列表 + 新建/编辑/删除）
/configs/new                 ConfigEdit 新建模式
/configs/{id}                ConfigEdit 编辑模式
```

详细 API 文档见 [docs/api-guide.md](docs/api-guide.md)。

## 5. 数据模型

```
crawl_config
  id, name, page_type (LIST_DETAIL|DETAIL_ONLY),
  selector_type (CSS|XPATH), status (ACTIVE|STOPPED, default STOPPED),
  created_at, updated_at

crawl_field（作为 crawl_config 的子资源，FK config_id）
  id, config_id, page_type (LIST|DETAIL), field_name, field_type (TEXT|NUMBER|DATE|URL),
  selector, created_at, updated_at

关系：CrawlConfig @OneToMany CrawlField，CascadeType.ALL + orphanRemoval=true
删除配置时自动级联删除所有字段。
```

## 6. 代码风格（核心约定）

**Java**：
- 类名 `PascalCase`、方法/变量 `camelCase`、常量 `SCREAMING_SNAKE_CASE`
- 优先用 `record` 做 DTO 和值对象
- 构造函数注入，不用 `@Autowired` 字段注入
- 所有 DTO 字段用 `@NotNull` / `@NotBlank` 校验

**Vue/JS**：
- `<script setup>` 语法，props 用 `defineProps`
- 状态管理走 Pinia（按 useXxxStore 命名）
- 组件文件 `PascalCase.vue`，其他 JS/TS 文件 `camelCase.js`

完整规范和反模式见 `~/.claude/rules/java/` 与 `~/.claude/rules/typescript/`。

## 7. TDD 模式（必读）

**每个新行为走三步循环**：
```
RED:    写一个失败测试（公共接口视角）
GREEN:  写最小实现让测试通过
REFACTOR: 重构保持绿色
```

**禁止**：
- 写完所有测试再写所有实现（水平切片）
- 测私有方法 / mock 内部协作者
- 写完代码后补测试（事后测试 ≠ TDD）

详细模板、覆盖率目标、断言风格见 [docs/tdd-guide.md](docs/tdd-guide.md)。

## 8. 深入文档指针

| 主题 | 文件 |
|------|------|
| 后端 + 前端架构、数据流 | [docs/architecture.md](docs/architecture.md) |
| API 端点、请求/响应示例、错误码 | [docs/api-guide.md](docs/api-guide.md) |
| 启动 / 测试 / 故障排查 / Known Issues | [docs/runbook.md](docs/runbook.md) |
| TDD 模板、覆盖率目标、断言风格 | [docs/tdd-guide.md](docs/tdd-guide.md) |
| M1 原始设计文档 | [docs/explore/M1-project-management-plan.md](docs/explore/M1-project-management-plan.md) |
| OpenSpec 规格（开发真相源） | `openspec/specs/<capability>/spec.md` |

## 9. Git 规范

分支：`feature/{name}` / `fix/{desc}` / `refactor/{desc}`
提交：`{type}: {描述}`，type: feat | fix | refactor | test | docs | chore
默认语言：中文。
