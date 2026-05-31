# data-persistence（数据持久化）

## Purpose

爬取结果的持久化存储，包括 raw_html 原始页面保留、custom_fields JSON 灵活存储、查询浏览、数据导出和实体级联删除。

## ADDED Requirements

### Requirement: 爬取数据实体存储
系统 SHALL 将爬取数据存储在以下实体中：list_page（每个访问的列表页一个）、list_item（从列表页提取的每个列表项一个）、article（每个访问的详情页一个）、detail_url（DETAIL_ONLY 模式中每个用户提供的 URL 一个）。所有实体保留爬取时的原始 HTML。

#### 场景：列表页保留 raw_html
- **WHEN** 爬取列表页
- **THEN** 系统将完整原始 HTML 内容存储在 list_page.raw_html 字段中，供后续重新解析

#### 场景：文章保留 raw_html
- **WHEN** 爬取详情页
- **THEN** 系统将完整原始 HTML 内容存储在 article.raw_html 字段中，供后续重新解析

### Requirement: 自定义字段存储
用户定义的字段值 SHALL 以 JSON 对象（custom_fields）存储在对应实体中。结构为扁平键值映射，键为用户定义的字段名。

#### 场景：为文章存储 custom_fields
- **WHEN** 提取产生字段值 {"文章标题": "Game 7 Preview", "作者": "John Doe", "发布日期": "2026-05-25"}
- **THEN** article.custom_fields JSON 列存储的正是该对象

### Requirement: 数据查询与浏览
系统 SHALL 提供只读 API 以浏览所有爬取数据，包括：list_pages（按 config_id 和 task_id 分页过滤）、list_items（按 list_page_id 分页过滤）、articles（按 config_id、task_id、关键词搜索 custom_fields 分页过滤）。

#### 场景：关键词搜索 + 分页查询文章列表
- **WHEN** 用户请求 GET /articles?config_id=1&keyword=warrior&page=1&size=20
- **THEN** 系统返回匹配 config_id=1 且任意 custom_fields 值包含 "warrior" 的文章，分页返回

#### 场景：按 list_page 浏览列表项
- **WHEN** 用户请求 GET /list-items?list_page_id=5
- **THEN** 系统返回属于列表页 5 的所有列表项

### Requirement: 数据导出
系统 SHALL 允许以 Excel (.xlsx) 和 JSON 格式导出文章数据。导出的每行对应一篇文章，每列对应一个 custom_fields 键。

#### 场景：导出为 JSON
- **WHEN** 用户请求 POST /articles/export?format=JSON
- **THEN** 系统返回所有文章（遵循当前过滤条件）的 JSON 数组，custom_fields 展开为顶级字段

#### 场景：导出为 Excel
- **WHEN** 用户请求 POST /articles/export?format=xlsx
- **THEN** 系统返回 Excel 文件，每行一篇文章，每列对应一个 custom_fields 键；所有文章共享同一列集合（所有键的并集）

### Requirement: 文章详情查看
系统 SHALL 提供单篇文章详情视图，展示文章 URL、状态、error_message（如有）、raw_html 和完整的 custom_fields 对象。

#### 场景：查看文章详情
- **WHEN** 用户请求 GET /articles/:id
- **THEN** 系统返回完整 article 实体，包括 raw_html 和 custom_fields

### Requirement: 实体删除
系统 SHALL 允许单独删除任意爬取数据实体（list_page、list_item、article）。

#### 场景：删除 list_page 级联到 list_items
- **WHEN** 用户删除 list_page
- **THEN** 所有关联的 list_items 也被删除（级联）
