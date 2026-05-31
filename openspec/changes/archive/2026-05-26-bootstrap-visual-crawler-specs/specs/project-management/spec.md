# project-management（项目管理）

## ADDED Requirements

### Requirement: 项目（站点）配置生命周期
系统 SHALL 允许用户创建、读取、更新和删除爬虫项目（以下简称"配置"）。每个配置代表一个目标站点的爬取设置。

#### 场景：创建新项目
- **WHEN** 用户提供项目名称、起始URL、页面类型（LIST_DETAIL 或 DETAIL_ONLY）、选择器类型（CSS 或 XPATH）
- **THEN** 系统创建项目，状态为 ACTIVE，并返回项目 ID

#### 场景：更新项目
- **WHEN** 用户修改现有项目的任何字段（名称、起始URL、页面类型、选择器类型）
- **THEN** 系统更新项目，并将 updated_at 设为当前时间戳

#### 场景：删除项目
- **WHEN** 用户删除一个项目
- **THEN** 系统级联删除所有关联的字段定义、任务、列表页、列表项和文章数据

#### 场景：查询项目列表
- **WHEN** 用户请求所有项目
- **THEN** 系统返回分页列表，每条记录包含 id、name、page_type、selector_type、status、created_at、updated_at

### Requirement: 页面类型模式
系统 SHALL 支持两种互斥的页面类型模式：

#### 场景：选择 LIST_DETAIL 模式
- **WHEN** 配置的 page_type 为 LIST_DETAIL
- **THEN** 系统要求同时定义 LIST 页面字段和 DETAIL 页面字段；爬取流程为：列表页 → 多个列表项 → 详情页

#### 场景：选择 DETAIL_ONLY 模式
- **WHEN** 配置的 page_type 为 DETAIL_ONLY
- **THEN** 系统只要求定义 DETAIL 页面字段；爬取流程为：用户提供的URL列表 → 直接详情页

### Requirement: 项目状态
配置 SHALL 拥有以下状态之一：ACTIVE（可用）或 STOPPED（停用）。状态不影响 API 操作，但作为运行时标志。

#### 场景：停用项目
- **WHEN** 用户将项目状态设置为 STOPPED
- **THEN** 新任务不得启动该项目；进行中的任务继续运行直至自然结束
