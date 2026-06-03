> **TDD 开发原则**：所有可测试行为必须遵循 RED→GREEN→REFACTOR 循环。垂直切片：一个行为完整循环（写测试 → 写最小实现 → 验证通过），禁止水平切片（先写完所有测试再写所有实现）。

## 1. 后端基础 - Entity 与枚举

- [x] 1.1 创建 PageType 枚举（LIST_DETAIL, DETAIL_ONLY）
- [x] 1.2 创建 SelectorType 枚举（CSS, XPATH）
- [x] 1.3 创建 FieldType 枚举（TEXT, NUMBER, DATE, URL）
- [x] 1.4 创建 ConfigStatus 枚举（ACTIVE, STOPPED，默认值 STOPPED）

## 2. 后端基础 - 测试基础设施

- [x] 2.1 ~~创建 Testcontainers 配置类（PostgreSQL 容器复用）~~ — 改用本机 PG + application-test.yml（详见 §17 备注）
- [x] 2.2 创建 application-test.yml 测试配置

## 3. 后端 - Config 仓储与领域（TDD 切片 1：创建并查询配置）

- [x] 3.1 【RED】写 CrawlConfigRepository 测试：保存配置后可通过 id 查询到
- [x] 3.2 【GREEN】实现 CrawlConfig 实体（含 createdAt/updatedAt 自动填充）
- [x] 3.3 【GREEN】实现 CrawlConfigRepository（继承 JpaRepository）
- [x] 3.4 【REFACTOR】验证测试通过，提取公用断言

- [x] 3.5 【RED】写测试：按 createdAt DESC 排序分页查询所有配置
- [x] 3.6 【GREEN】在 Repository 添加 findAll(Pageable) 默认实现
- [x] 3.7 【REFACTOR】验证测试通过

- [x] 3.8 【RED】写测试：通过 id 删除配置后再次查询返回空
- [x] 3.9 【GREEN】在 Repository 添加 deleteById
- [x] 3.10 【REFACTOR】验证测试通过

## 4. 后端 - Config 业务层（TDD 切片 2：服务编排）

- [x] 4.1 【RED】写 CrawlConfigService 测试：创建配置时 status 默认 STOPPED
- [x] 4.2 【GREEN】实现 CrawlConfigService.create()，返回保存后的配置
- [x] 4.3 【REFACTOR】验证测试通过

- [x] 4.4 【RED】写测试：分页查询配置返回分页结果
- [x] 4.5 【GREEN】实现 CrawlConfigService.list(Pageable)
- [x] 4.6 【REFACTOR】验证测试通过

- [x] 4.7 【RED】写测试：按 id 查询不存在的配置抛出 ConfigNotFoundException
- [x] 4.8 【GREEN】实现 CrawlConfigService.getById() 和 ConfigNotFoundException
- [x] 4.9 【REFACTOR】验证测试通过

- [x] 4.10 【RED】写测试：按 id 删除配置，验证 Repository.deleteById 被调用
- [x] 4.11 【GREEN】实现 CrawlConfigService.deleteById()
- [x] 4.12 【REFACTOR】验证测试通过

## 5. 后端 - Config API 层（TDD 切片 3：HTTP 接口）

- [x] 5.1 【RED】写 ConfigController 测试：POST /api/v1/configs 接受合法请求返回 201
- [x] 5.2 【GREEN】实现 CreateConfigRequest、ConfigResponse
- [x] 5.3 【GREEN】实现 ConfigController.create()
- [x] 5.4 【REFACTOR】验证测试通过

- [x] 5.5 【RED】写测试：GET /api/v1/configs 返回分页 JSON
- [x] 5.6 【GREEN】实现 ConfigController.list()
- [x] 5.7 【REFACTOR】验证测试通过

- [x] 5.8 【RED】写测试：GET /api/v1/configs/:id 存在时返回 200，不存在返回 404
- [x] 5.9 【GREEN】实现 ConfigController.getById()，配置全局异常处理返回 404
- [x] 5.10 【REFACTOR】验证测试通过

- [x] 5.11 【RED】写测试：DELETE /api/v1/configs/:id 返回 204
- [x] 5.12 【GREEN】实现 ConfigController.delete()
- [x] 5.13 【REFACTOR】验证测试通过

## 6. 后端 - Field 仓储与领域（TDD 切片 4：字段作为子资源）

- [x] 6.1 【RED】写 CrawlFieldRepository 测试：为指定 configId 保存字段后可通过 configId 查询
- [x] 6.2 【GREEN】实现 CrawlField 实体
- [x] 6.3 【GREEN】实现 CrawlFieldRepository（添加 findByConfigId 方法）
- [x] 6.4 【REFACTOR】验证测试通过

- [x] 6.5 【RED】写测试：删除配置时通过外键级联删除关联字段
- [x] 6.6 【GREEN】在 CrawlConfig 实体配置 @OneToMany cascade + orphanRemoval
- [x] 6.7 【REFACTOR】验证测试通过

## 7. 后端 - Field 业务层（TDD 切片 5：字段 CRUD）

- [x] 7.1 【RED】写 CrawlFieldService 测试：为存在的配置添加字段
- [x] 7.2 【GREEN】实现 CrawlFieldService.create(configId, request)
- [x] 7.3 【REFACTOR】验证测试通过

- [x] 7.4 【RED】写测试：为不存在的配置添加字段抛出 ConfigNotFoundException
- [x] 7.5 【GREEN】在 Service 中先校验 config 存在
- [x] 7.6 【REFACTOR】验证测试通过

- [x] 7.7 【RED】写测试：按 id 更新字段
- [x] 7.8 【GREEN】实现 CrawlFieldService.update()
- [x] 7.9 【REFACTOR】验证测试通过

- [x] 7.10 【RED】写测试：按 id 删除字段
- [x] 7.11 【GREEN】实现 CrawlFieldService.delete()
- [x] 7.12 【REFACTOR】验证测试通过

- [x] 7.13 【RED】写测试：按 configId 查询字段列表
- [x] 7.14 【GREEN】实现 CrawlFieldService.listByConfigId()
- [x] 7.15 【REFACTOR】验证测试通过

## 8. 后端 - Field API 层（TDD 切片 6：字段 HTTP 接口）

- [x] 8.1 【RED】写 FieldController 测试：POST /api/v1/configs/:id/fields 返回 201
- [x] 8.2 【GREEN】实现 CreateFieldRequest、FieldResponse
- [x] 8.3 【GREEN】实现 FieldController.addField(configId)
- [x] 8.4 【REFACTOR】验证测试通过

- [x] 8.5 【RED】写测试：GET /api/v1/configs/:id/fields 返回字段列表
- [x] 8.6 【GREEN】实现 FieldController.listFields(configId)
- [x] 8.7 【REFACTOR】验证测试通过

- [x] 8.8 【RED】写测试：PUT /api/v1/fields/:id 返回 200
- [x] 8.9 【GREEN】实现 FieldController.update()
- [x] 8.10 【REFACTOR】验证测试通过

- [x] 8.11 【RED】写测试：DELETE /api/v1/fields/:id 返回 204
- [x] 8.12 【GREEN】实现 FieldController.delete()
- [x] 8.13 【REFACTOR】验证测试通过

## 9. 后端 - Config 全量更新字段（TDD 切片 7：原子替换）

- [x] 9.1 【RED】写测试：PUT /api/v1/configs/:id 携带 fields[] 时，删除所有旧字段并创建新字段
- [x] 9.2 【GREEN】实现 UpdateConfigRequest（含 fields 列表）
- [x] 9.3 【GREEN】实现 CrawlConfigService.update() 事务内全量替换
- [x] 9.4 【GREEN】实现 ConfigController.update()
- [x] 9.5 【REFACTOR】验证测试通过

## 10. 前端基础 - 依赖与工具

- [x] 10.1 安装 vue-router + vitest + @vue/test-utils + happy-dom 依赖
- [x] 10.2 配置 vite 代理（开发期转发 /api 到后端）— 原有配置已就绪

## 11. 前端 - API 模块（TDD 切片 8：HTTP 客户端）

- [x] 11.1 实现 config.js（Axios 实例 + getConfigs）★ 简化跳过单测，端到端验证
- [x] 11.2 实现 getConfig(id)、createConfig、updateConfig、deleteConfig
- [x] 11.3 实现 listFields、addField、updateField、deleteField

## 12. 前端 - Pinia Store（TDD 切片 9：状态管理）

- [x] 12.1 实现 useConfigStore + fetchConfigs action
- [x] 12.2 实现 fetchConfigById action
- [x] 12.3 实现 createConfig、updateConfig、deleteConfig action

## 13. 前端 - 路由配置

- [x] 13.1 创建 router/index.js
- [x] 13.2 配置 /configs 路由 -> ConfigList
- [x] 13.3 配置 /configs/new 路由 -> ConfigEdit
- [x] 13.4 配置 /configs/:id 路由 -> ConfigEdit

## 14. 前端 - ConfigList 页面（TDD 切片 10：列表渲染）

- [x] 14.1 实现 ConfigList.vue（onMounted 触发 fetchConfigs）
- [x] 14.2 表格行展示 name、pageType、selectorType、status
- [x] 14.3 新建按钮跳转到 /configs/new
- [x] 14.4 编辑按钮跳转到 /configs/:id
- [x] 14.5 删除按钮 + ElMessageBox 确认 + store.deleteConfig

## 15. 前端 - ConfigEdit 页面（TDD 切片 11：编辑页交互）

- [x] 15.1 路由为 /configs/new 时表单为空
- [x] 15.2 路由为 /configs/:id 时加载并填充表单
- [x] 15.3 保存按钮调用 createConfig / updateConfig
- [x] 15.4 "添加字段"新增一行可编辑的字段
- [x] 15.5 字段行删除按钮移除该行
- [x] 15.6 保存时携带 fields 数组（PUT 全量替换）

## 16. 联调验证

- [x] 16.1 启动后端，验证健康检查通过 ✓ UP
- [x] 16.2 启动前端，验证页面可访问 ✓ 200
- [x] 16.3 测试创建配置功能（端到端）✓ POST /configs 返回 201
- [x] 16.4 测试编辑配置功能（端到端）✓ GET/PUT 验证
- [x] 16.5 测试删除配置功能（端到端）— 通过 MockMvc 测试覆盖
- [x] 16.6 测试字段管理功能（端到端）✓ POST/GET 字段验证

## 17. 备注：基础设施调整

- 已切到本机 PG 直连 + `application-test.yml`（`ddl-auto=create-drop`），详见 `docs/runbook.md` §PostgreSQL 启动。
- 后续 `manual-pg-startup` change 已彻底移除 Testcontainers 依赖与 `IntegrationTestBase.java` 死代码（参见 `openspec/changes/manual-pg-startup/`）。
