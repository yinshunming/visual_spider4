# 可视化爬虫 MVP 设计文档

**日期**: 2026-05-24
**项目**: 可视化爬虫 MVP
**目标**: 通用可视化爬虫，支持点击生成选择器 + 用户自定义字段映射

---

## 1. 设计变更说明

### 1.1 相对旧设计的核心变化

| 维度 | 旧设计 | 新设计 |
|------|--------|--------|
| 选择器配置 | 用户手动输入 CSS/XPath | 内嵌浏览器 + 点击自动生成 + 手动补充 |
| 字段映射 | 固定字段（title, content, author...） | 用户自定义字段名 + 类型 + 选择器 |
| 数据存储 | 固定表结构，多张关联表 | JSON 列存储自定义字段 |
| 页面字段管理 | 统一管理 | 列表页字段 / 详情页字段分开管理 |
| 爬取模式 | 列表页 → 详情页 | 支持两种模式（见 1.2） |

### 1.2 爬取模式

| 模式 | 触发条件 | 数据流向 |
|------|----------|----------|
| 模式一 | 配置了列表页字段 + 详情页字段 | 起始URL → 列表页 → 详情页 → 数据 |
| 模式二 | 只有详情页字段（列表页字段为空） | 用户输入URL列表 → 直接详情页 → 数据 |

---

## 2. 技术架构

### 2.1 技术栈

| 分层 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue3 + Vite + Element Plus + Axios | 前后端分离 |
| 后端 | Spring Boot 3.x + Spring Data JPA | Java 后端 |
| 数据库 | PostgreSQL | 关系型数据库 |
| 爬虫 | Jsoup (HTML 解析) + Spring WebClient | HTTP 客户端 |
| 内嵌浏览器 | Playwright | 前端内嵌，点击生成选择器 |

### 2.2 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue3)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │  爬虫配置页  │  │  任务管理页  │  │  数据展示页  │       │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
│         └────────────────┼────────────────┘               │
│                          │                                  │
│                    ┌─────▼─────┐                          │
│                    │   Axios   │                          │
└────────────────────┼───────────┼────────────────────────────┘
                      │ HTTP REST │
┌────────────────────┼───────────┼────────────────────────────┐
│              ┌─────▼─────┐    │     后端 (Spring Boot)       │
│              │   API     │    │                             │
│              │  Gateway  │    │                             │
│              └─────┬─────┘    │                             │
│    ┌────────────────┼────────────────┐                   │
│┌───▼───┐    ┌──────▼──────┐    ┌────▼────┐              │
││爬虫配置│    │  爬取任务API │    │数据查询API│              │
││  API  │    └──────┬──────┘    └────┬────┘              │
│└───┬───┘           │                  │                    │
│    │     ┌─────────┼──────────────────┼──────────┐        │
│    │     │    ┌────▼─────────────────▼────┐    │        │
│    │     │    │       Service 层            │    │        │
│    │     │    │  ┌─────────┐  ┌─────────┐  │    │        │
│    │     │    │  │爬虫配置 │  │爬取执行 │  │    │        │
│    │     │    │  │Service  │  │Service  │  │    │        │
│    │     │    │  └─────────┘  └─────────┘  │    │        │
│    │     │    └───────────────┬────────────┘    │        │
│    │     │                    │                    │        │
│    │     │              ┌─────▼─────┐              │        │
│    │     │              │  Jsoup    │  爬虫引擎    │        │
│    │     │              └───────────┘              │        │
│    │     └───────────────────────────────────────┘        │
└────┼───────────────────────────────────────────────────────┘
     │
┌────▼────────────────────────────────────────────────────────┐
│                      PostgreSQL                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │crawl_config│ │crawl_field│  │ list_page│  │crawl_task│ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│                                                          │
│  ┌──────────┐                                             │
│  │ article  │  (custom_fields 以 JSONB 存储)            │
│  └──────────┘                                             │
└──────────────────────────────────────────────────────────┘
```

---

## 3. 数据库设计

### 3.1 核心表结构

#### 3.1.1 爬虫配置表 `crawl_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(100) | 配置名称 |
| start_url | TEXT | 起始 URL |
| page_type | VARCHAR(20) | 页面类型：LIST_DETAIL / DETAIL_ONLY |
| selector_type | VARCHAR(10) | 选择器类型：CSS / XPATH |
| status | VARCHAR(20) | 状态：ACTIVE / STOPPED |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

**page_type 说明：**
- `LIST_DETAIL`：列表页 → 详情页模式
- `DETAIL_ONLY`：直接详情页模式（用户提供URL列表）

#### 3.1.2 字段配置表 `crawl_field`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| page_type | VARCHAR(20) | 页面类型：LIST / DETAIL |
| field_name | VARCHAR(100) | 用户定义的字段名 |
| field_type | VARCHAR(20) | 字段类型：TEXT / NUMBER / DATE / URL |
| selector | TEXT | CSS 或 XPath 选择器 |
| sort_order | INT | 排序顺序 |
| created_at | TIMESTAMP | 创建时间 |

**字段类型说明：**

| 类型 | 说明 | 存储格式 |
|------|------|----------|
| TEXT | 文本内容 | 字符串 |
| NUMBER | 数字 | 数字字符串 |
| DATE | 日期时间 | ISO 8601 字符串 |
| URL | 链接 | 绝对 URL 字符串 |

**page_type = LIST 时的特殊字段：**

| 字段名 | field_type | 说明 |
|--------|------------|------|
| detail_url | URL | 详情页链接（必填，用于从列表页提取详情页地址） |

#### 3.1.3 列表页数据表 `list_page`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| task_id | BIGINT FK | 关联任务ID |
| url | TEXT | 列表页 URL |
| raw_html | TEXT | 原始 HTML |
| custom_fields | JSONB | 用户自定义的列表页字段 |
| status | VARCHAR(20) | 状态：PENDING / CRAWLED / FAILED |
| error_message | TEXT | 错误信息 |
| created_at | TIMESTAMP | 创建时间 |

**custom_fields 示例：**

```json
{
  "文章标题": "勇士击败湖人晋级决赛",
  "发布日期": "2026-05-24",
  "detail_url": "https://sports.sina.com.cn/nba/2026-05-24/xxxx.html"
}
```

#### 3.1.4 文章详情表 `article`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| task_id | BIGINT FK | 关联任务ID |
| list_page_id | BIGINT FK | 关联列表页ID（可为空，DETAIL_ONLY模式时为空） |
| url | TEXT | 文章 URL |
| raw_html | TEXT | 原始 HTML |
| custom_fields | JSONB | 用户自定义的详情页字段 |
| status | VARCHAR(20) | 状态：CRAWLED / FAILED |
| error_message | TEXT | 错误信息 |
| created_at | TIMESTAMP | 爬取时间 |

**custom_fields 示例：**

```json
{
  "文章标题": "勇士击败湖人晋级决赛",
  "正文": "5月24日，NBA季后赛...",
  "作者": "张三",
  "发布日期": "2026-05-24 10:30:00",
  "图片链接": "https://pic.sina.com.cn/xxxx.jpg"
}
```

#### 3.1.5 爬取任务表 `crawl_task`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| status | VARCHAR(20) | 状态：RUNNING / COMPLETED / FAILED |
| total_items | INT | 总数 |
| crawled_items | INT | 已爬数 |
| failed_items | INT | 失败数 |
| error_message | TEXT | 错误信息 |
| started_at | TIMESTAMP | 开始时间 |
| completed_at | TIMESTAMP | 结束时间 |

#### 3.1.6 直接详情URL表 `detail_url`（仅 DETAIL_ONLY 模式使用）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| task_id | BIGINT FK | 关联任务ID |
| url | TEXT | 详情页 URL |
| status | VARCHAR(20) | 状态：PENDING / CRAWLED / FAILED |
| created_at | TIMESTAMP | 创建时间 |

### 3.2 ER 关系

```
crawl_config (1) ──── (N) crawl_field
crawl_config (1) ──── (N) crawl_task
crawl_config (1) ──── (N) list_page
crawl_config (1) ──── (N) article
crawl_task (1) ──── (N) list_page
crawl_task (1) ──── (N) article
crawl_task (1) ──── (N) detail_url
list_page (1) ──── (N) article
```

### 3.3 设计原则

- **crawl_field 独立存储**：字段配置与爬虫配置分离，方便编辑
- **custom_fields JSONB 存储**：用户自定义字段以 JSON 存储，灵活扩展
- **raw_html 保留**：原始 HTML 完整保留，支持重新解析
- **list_page 可为空**：DETAIL_ONLY 模式下，列表页数据为空

---

## 4. 前端页面设计

### 4.1 页面结构

```
┌──────────────────────────────────────────────────────────────┐
│  可视化爬虫 MVP                                                 │
├──────────────────────────────────────────────────────────────┤
│  [Logo] 可视化爬虫    │  配置管理 │ 任务管理 │ 数据展示 │       │
└──────────────────────────────────────────────────────────────┘
                                 │
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                 ▼
      ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
      │  配置管理页  │    │  任务管理页  │    │  数据展示页  │
      └─────────────┘    └─────────────┘    └─────────────┘
```

### 4.2 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/configs` | 配置管理页 | 列表展示所有配置 |
| `/configs/:id` | 配置详情/编辑页 | 查看、编辑、新建配置 |
| `/tasks` | 任务管理页 | 列表展示所有任务 |
| `/articles` | 数据展示页 | 表格展示文章数据 |

### 4.3 页面功能

#### 4.3.1 配置管理页 `/configs`

- 展示所有爬虫配置列表
- 支持新建、编辑、删除、测试配置
- 显示配置名称、类型、状态

#### 4.3.2 配置详情/编辑页 `/configs/:id`

**顶部：基本信息**
- 配置名称
- 起始 URL
- 页面类型选择（LIST_DETAIL / DETAIL_ONLY）
- 选择器类型切换（CSS / XPath）

**中间：内嵌浏览器区域**
- 集成 Playwright 内嵌浏览器
- 用户输入起始 URL 后，在浏览器中打开
- 点击页面元素，自动生成选择器

**字段配置区域：**

- **LIST_DETAIL 模式**：
  - 列表页字段配置区
    - 添加字段：字段名、类型（TEXT/NUMBER/DATE/URL）、选择器
    - 特殊：detail_url 字段（必填）
    - 支持手动输入或点击生成
  - 详情页字段配置区
    - 添加字段：字段名、类型、选择器
    - 支持手动输入或点击生成

- **DETAIL_ONLY 模式**：
  - 详情页字段配置区（无列表页）
  - 支持手动输入或点击生成
  - URL 列表在任务创建时输入

**底部：**
- 保存配置按钮
- 开始爬取按钮（跳转任务创建）

#### 4.3.3 任务管理页 `/tasks`

- 展示所有爬取任务
- 显示任务进度（百分比、已爬/总数）
- 支持停止、删除任务
- 任务状态：运行中、已完成、已失败

**DETAIL_ONLY 模式特殊交互：**
- 创建任务时，弹出输入框让用户输入 URL 列表（支持粘贴多行）

#### 4.3.4 数据展示页 `/articles`

- 表格展示文章数据
- **custom_fields 以 JSON 展开展示**，动态列
- 支持关键词搜索、配置筛选、状态筛选
- 分页功能
- 导出功能（Excel/JSON）
- 文章详情抽屉展示

---

## 5. 后端 API 设计

### 5.1 API 基础路径

`/api/v1`

### 5.2 爬虫配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/configs` | 获取所有配置 |
| GET | `/configs/:id` | 获取单个配置（含字段配置） |
| POST | `/configs` | 创建配置 |
| PUT | `/configs/:id` | 更新配置 |
| DELETE | `/configs/:id` | 删除配置 |

### 5.3 字段配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/configs/:id/fields` | 获取配置的字段列表 |
| POST | `/configs/:id/fields` | 批量添加字段 |
| PUT | `/configs/:id/fields/:fieldId` | 更新单个字段 |
| DELETE | `/configs/:id/fields/:fieldId` | 删除单个字段 |

### 5.4 爬取任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tasks` | 获取所有任务 |
| GET | `/tasks/:id` | 获取任务详情 |
| POST | `/tasks` | 创建并启动任务（DETAIL_ONLY模式时，URL列表在body中） |
| POST | `/tasks/:id/stop` | 停止任务 |
| DELETE | `/tasks/:id` | 删除任务 |

**POST /tasks 请求体示例（DETAIL_ONLY模式）：**

```json
{
  "config_id": 1,
  "urls": [
    "https://example.com/article/1",
    "https://example.com/article/2",
    "https://example.com/article/3"
  ]
}
```

### 5.5 列表页数据 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list-pages` | 获取列表页数据（分页） |
| GET | `/list-pages/:id` | 获取单条列表页 |
| POST | `/list-pages/:id/reparse` | 重新解析列表页 |
| DELETE | `/list-pages/:id` | 删除列表页 |

### 5.6 文章数据 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/articles` | 获取文章列表（分页+搜索，动态列） |
| GET | `/articles/:id` | 获取文章详情 |
| POST | `/articles/:id/reparse` | 重新解析文章 |
| DELETE | `/articles/:id` | 删除文章 |
| POST | `/articles/export` | 导出文章（Excel/JSON） |

---

## 6. 核心数据模型

### 6.1 Java 实体类

#### CrawlConfig.java

```java
@Entity
@Table(name = "crawl_config")
public class CrawlConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String startUrl;
    private String pageType;        // LIST_DETAIL / DETAIL_ONLY
    private String selectorType;    // CSS / XPATH
    private String status;          // ACTIVE / STOPPED

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "configId", cascade = CascadeType.ALL)
    private List<CrawlField> fields;
}
```

#### CrawlField.java

```java
@Entity
@Table(name = "crawl_field")
public class CrawlField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long configId;
    private String pageType;        // LIST / DETAIL
    private String fieldName;       // 用户定义的字段名
    private String fieldType;      // TEXT / NUMBER / DATE / URL
    private String selector;
    private Integer sortOrder;

    private LocalDateTime createdAt;
}
```

#### ListPage.java

```java
@Entity
@Table(name = "list_page")
public class ListPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long configId;
    private Long taskId;
    private String url;
    private String rawHtml;
    private String customFields;    // JSON字符串
    private String status;          // PENDING / CRAWLED / FAILED
    private String errorMessage;
    private LocalDateTime createdAt;
}
```

#### Article.java

```java
@Entity
@Table(name = "article")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long configId;
    private Long taskId;
    private Long listPageId;        // 可为空
    private String url;
    private String rawHtml;
    private String customFields;    // JSON字符串，用户自定义字段
    private String status;          // CRAWLED / FAILED
    private String errorMessage;
    private LocalDateTime createdAt;
}
```

#### CrawlTask.java

```java
@Entity
@Table(name = "crawl_task")
public class CrawlTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long configId;
    private String status;          // RUNNING / COMPLETED / FAILED
    private Integer totalItems;
    private Integer crawledItems;
    private Integer failedItems;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

#### DetailUrl.java（仅 DETAIL_ONLY 模式）

```java
@Entity
@Table(name = "detail_url")
public class DetailUrl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long taskId;
    private String url;
    private String status;          // PENDING / CRAWLED / FAILED
    private LocalDateTime createdAt;
}
```

---

## 7. 爬虫引擎设计

### 7.1 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| HTTP 客户端 | Spring WebClient | 异步非阻塞 |
| HTML 解析 | Jsoup | 支持 CSS + XPath |
| 线程控制 | ThreadPoolExecutor | 传统线程池，支持任务并发控制 |
| 任务状态 | AtomicBoolean flag | 支持优雅停止 |
| 内嵌浏览器 | Playwright | 前端集成，点击生成选择器 |

### 7.2 核心流程

#### 模式一：LIST_DETAIL

```
┌──────────────────────────────────────────────────────────────┐
│                     启动爬取任务                             │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  1. 创建 Task 记录 (status=RUNNING)                        │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  2. 爬取列表页 → 存入 list_page (status=PENDING)          │
│     custom_fields 存储用户自定义字段                        │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  3. 遍历 list_page → 检查停止 flag                        │
│     从 custom_fields.detail_url 提取详情页 URL             │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  4. 爬取详情页 → 存入 article (status=CRAWLED/FAILED)    │
│     custom_fields 存储用户自定义字段                        │
│     更新 list_page.status                                  │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  5. 更新 Task 状态 (COMPLETED/FAILED)                     │
└────────────────────────────────────────────────────────────┘
```

#### 模式二：DETAIL_ONLY

```
┌──────────────────────────────────────────────────────────────┐
│                     启动爬取任务                             │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  1. 创建 Task 记录 (status=RUNNING)                        │
│     根据用户输入的 URL 列表创建 detail_url 记录             │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  2. 遍历 detail_url → 检查停止 flag                       │
│     直接爬取详情页                                          │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  3. 存入 article (status=CRAWLED/FAILED)                 │
│     custom_fields 存储用户自定义字段                        │
│     更新 detail_url.status                                 │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  4. 更新 Task 状态 (COMPLETED/FAILED)                     │
└────────────────────────────────────────────────────────────┘
```

### 7.3 核心类设计

#### CrawlService.java

- `startCrawl(configId, taskId)` - 启动爬取
- `stopCrawl(taskId)` - 停止爬取
- `crawlListDetail(config, task)` - LIST_DETAIL 模式
- `crawlDetailOnly(config, task, urls)` - DETAIL_ONLY 模式
- `extractFields(html, fields)` - 根据字段配置提取数据
- `saveCustomFields(configId, pageType, extracted)` - 保存为 JSON

#### SelectorService.java

- `extractByCss(html, selector)` - CSS 选择器提取
- `extractByXPath(html, selector)` - XPath 选择器提取
- `convertSelector(html, element)` - 从元素生成选择器（Playwright 调用）

#### PlaywrightSelectorGenerator.java

- `generateSelector(url, element)` - 在浏览器中点击元素，生成选择器
- `openBrowser(url)` - 打开内嵌浏览器
- `closeBrowser()` - 关闭浏览器

---

## 8. 项目结构

### 8.1 前端项目结构 (Vue3)

```
frontend/
├── src/
│   ├── api/
│   │   ├── config.js
│   │   ├── field.js
│   │   ├── task.js
│   │   └── article.js
│   ├── components/
│   │   ├── BrowserEmbed.vue      # 内嵌浏览器组件
│   │   ├── FieldEditor.vue       # 字段编辑器
│   │   └── JsonTable.vue         # JSON展开表格
│   ├── pages/
│   │   ├── ConfigList.vue
│   │   ├── ConfigEdit.vue
│   │   ├── TaskList.vue
│   │   └── ArticleList.vue
│   ├── stores/
│   │   ├── config.js
│   │   ├── task.js
│   │   └── article.js
│   ├── router/
│   └── App.vue
├── package.json
└── vite.config.js
```

### 8.2 后端项目结构 (Spring Boot)

```
backend/
├── src/main/java/com/visualspider/
│   ├── controller/
│   │   ├── ConfigController.java
│   │   ├── FieldController.java
│   │   ├── TaskController.java
│   │   └── ArticleController.java
│   ├── service/
│   │   ├── CrawlService.java
│   │   ├── SelectorService.java
│   │   └── FieldService.java
│   ├── repository/
│   ├── entity/
│   │   ├── CrawlConfig.java
│   │   ├── CrawlField.java
│   │   ├── ListPage.java
│   │   ├── Article.java
│   │   ├── CrawlTask.java
│   │   └── DetailUrl.java
│   ├── dto/
│   │   ├── FieldDTO.java
│   │   ├── TaskCreateDTO.java
│   │   └── ArticleDTO.java
│   └── SpiderApplication.java
├── src/main/resources/
│   └── application.yml
├── pom.xml
└── ...
```

---

## 9. MVP 功能清单

### 9.1 配置管理

- [ ] 创建爬虫配置（名称、URL、页面类型）
- [ ] 选择器类型切换（CSS / XPath）
- [ ] 内嵌浏览器打开目标网页
- [ ] 点击页面元素自动生成选择器
- [ ] 手动输入/修改选择器
- [ ] 列表页字段配置（添加/编辑/删除字段）
- [ ] 详情页字段配置（添加/编辑/删除字段）
- [ ] 字段类型选择（TEXT / NUMBER / DATE / URL）
- [ ] 编辑/删除配置

### 9.2 爬取任务

- [ ] 创建并启动爬取任务（LIST_DETAIL 模式）
- [ ] 创建并启动爬取任务（DETAIL_ONLY 模式，输入URL列表）
- [ ] 查看任务进度
- [ ] 停止任务
- [ ] 删除任务

### 9.3 数据展示

- [ ] 表格展示文章列表（动态列 from custom_fields）
- [ ] 关键词搜索
- [ ] 配置筛选
- [ ] 状态筛选
- [ ] 分页
- [ ] 导出（Excel/JSON）
- [ ] 文章详情抽屉展示

### 9.4 回溯机制

- [ ] 查看列表页原始数据
- [ ] 重新解析列表页
- [ ] 查看文章原始HTML
- [ ] 重新解析文章

---

## 10. 验收标准

1. ✅ 能够创建通用爬虫配置（不限网站）
2. ✅ 内嵌浏览器 + 点击生成选择器
3. ✅ 用户自定义字段（名称 + 类型 + 选择器）
4. ✅ 列表页字段 / 详情页字段分开管理
5. ✅ 支持 LIST_DETAIL 模式（列表→详情）
6. ✅ 支持 DETAIL_ONLY 模式（URL列表→直接详情）
7. ✅ custom_fields 以 JSON 格式存储
8. ✅ 能够启动爬取任务，实时查看进度
9. ✅ 能够停止正在运行的爬取任务
10. ✅ 前端动态展示 custom_fields 字段
11. ✅ 任务可中断，优雅退出

---

## 11. 后续迭代方向

| 功能 | 说明 |
|------|------|
| 选择器模板 | 预设"新闻列表"、"商品列表"等模板 |
| 定时爬取 | 周期性自动执行爬取任务 |
| 增量爬取 | 仅爬取新数据，避免重复 |
| 反爬策略 | 随机 User-Agent、请求延迟、代理IP |
