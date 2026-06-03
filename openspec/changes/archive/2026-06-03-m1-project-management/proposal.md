## Why

可视化爬虫管理系统需要完整的项目管理模块，支持用户创建、配置和管理爬虫任务。当前系统缺少配置和字段的CRUD能力，无法支撑后续爬取任务的执行。项目一期（M1）聚焦于项目管理基础功能的完整闭环，包括后端API、前端页面和测试。

## What Changes

### 后端
- 新增 `CrawlConfig` 实体（name, pageType, selectorType, status）
- 新增 `CrawlField` 实体（fieldName, fieldType, selector, pageType）
- 实现配置和字段的Repository层（含 @DataJpaTest 集成测试，连接本机 PostgreSQL 服务）
- 实现配置和字段的Service层（含单元测试）
- 实现配置和字段的Controller层（REST API，含MockMvc测试）
- 配置删除时级联删除关联字段

### 前端
- 安装配置 vue-router
- 新增 `/configs` 列表页（ConfigList.vue）
- 新增 `/configs/new` 新建配置页
- 新增 `/configs/:id` 编辑配置页（详情编辑合一）
- 实现 useConfigStore（Pinia状态管理）
- 实现 config API 模块（Axios封装）

### API 端点
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/configs | 创建配置 |
| GET | /api/v1/configs | 分页查询 |
| GET | /api/v1/configs/:id | 获取配置详情 |
| PUT | /api/v1/configs/:id | 更新配置（全量替换字段） |
| DELETE | /api/v1/configs/:id | 删除配置（级联删除字段） |
| GET | /api/v1/configs/:id/fields | 获取字段列表 |
| POST | /api/v1/configs/:id/fields | 添加字段 |
| PUT | /api/v1/fields/:id | 更新字段 |
| DELETE | /api/v1/fields/:id | 删除字段 |

## Capabilities

### New Capabilities
- `project-management`: 爬虫配置和字段的完整生命周期管理，支持LIST_DETAIL和DETAIL_ONLY两种页面类型，支持CSS和XPATH选择器

### Modified Capabilities
- 无（项目初始化阶段，无现有capabilities需要修改）

## Impact

### 后端影响
- 新增 `backend/src/main/java/com/visualspider/entity/CrawlConfig.java`
- 新增 `backend/src/main/java/com/visualspider/entity/CrawlField.java`
- 新增 `backend/src/main/java/com/visualspider/repository/CrawlConfigRepository.java`
- 新增 `backend/src/main/java/com/visualspider/repository/CrawlFieldRepository.java`
- 新增 `backend/src/main/java/com/visualspider/service/CrawlConfigService.java`
- 新增 `backend/src/main/java/com/visualspider/service/CrawlFieldService.java`
- 新增 `backend/src/main/java/com/visualspider/controller/ConfigController.java`
- 新增 `backend/src/main/java/com/visualspider/controller/FieldController.java`
- 新增 DTO类（request/response）
- 新增自定义异常类
- 新增对应测试类

### 前端影响
- 安装 `vue-router` 依赖
- 新增 `frontend/src/api/config.js`
- 新增 `frontend/src/stores/configStore.js`
- 新增 `frontend/src/views/ConfigList.vue`
- 新增 `frontend/src/views/ConfigEdit.vue`
- 更新 `frontend/src/router/index.js`

### 数据库影响
- 新增 `crawl_config` 表
- 新增 `crawl_field` 表（含外键约束）

### 测试影响
- Repository 层使用 @DataJpaTest 连接本机手工启动的 PostgreSQL 服务（库 `visual_spider4_test`）
- Service层使用Mockito模拟Repository
- Controller层使用MockMvc进行API测试
