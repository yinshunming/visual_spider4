# 可视化爬虫 MVP 设计文档

**日期**: 2026-05-24
**项目**: 可视化爬虫 MVP
**目标**: 爬取 NBA 新浪网资讯文章（列表页 + 内容页）

---

## 1. 技术架构

### 1.1 技术栈

| 分层 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue3 + Vite + Element Plus + Axios | 前后端分离 |
| 后端 | Spring Boot 3.x + Spring Data JPA | Java 后端 |
| 数据库 | PostgreSQL | 关系型数据库 |
| 爬虫 | Jsoup (HTML 解析) + Spring WebClient | HTTP 客户端 |

### 1.2 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue3)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │  爬虫配置页  │  │  任务管理页  │  │  数据展示页  │       │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
│         └────────────────┼────────────────┘               │
│                          │                                  │
│                    ┌─────▼─────┐                            │
│                    │   Axios   │                            │
└────────────────────┼───────────┼────────────────────────────┘
                     │ HTTP REST │
┌────────────────────┼───────────┼────────────────────────────┐
│              ┌─────▼─────┐    │     后端 (Spring Boot)     │
│              │   API     │    │                             │
│              │  Gateway  │    │                             │
│              └─────┬─────┘    │                             │
│    ┌────────────────┼────────────────┐                    │
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
│  │crawl_config│  │list_page │  │ article  │  │crawl_task│ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 工作流程

1. 用户在前端**配置页**输入起始 URL，选择内容类型（列表/详情）
2. 通过 CSS 或 XPath 选择器配置要提取的字段
3. 发起爬取任务 → 后端执行爬虫
4. 数据存入 PostgreSQL
5. 前端**数据展示页**以表格形式呈现，支持搜索过滤

---

## 2. 数据库设计

### 2.1 核心表结构

#### 2.1.1 爬虫配置表 `crawl_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(100) | 配置名称，如"NBA资讯列表" |
| start_url | TEXT | 起始 URL |
| page_type | VARCHAR(20) | 页面类型：LIST / DETAIL |
| list_rules | TEXT | 列表页规则 JSON |
| detail_rules | TEXT | 详情页规则 JSON |
| detail_url_rule | TEXT | 详情页 URL 规则 |
| selector_type | VARCHAR(10) | 选择器类型：CSS / XPATH |
| status | VARCHAR(20) | 状态：ACTIVE / STOPPED |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### 2.1.2 列表页数据表 `list_page`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| task_id | BIGINT FK | 关联任务ID |
| url | TEXT | 列表页 URL |
| raw_html | TEXT | 原始 HTML（保留用于回溯重做） |
| title | VARCHAR(500) | 文章标题 |
| article_url | TEXT | 文章详情页 URL |
| publish_date | TIMESTAMP | 发布日期 |
| status | VARCHAR(20) | 状态：PENDING / CRAWLED / FAILED |
| error_message | TEXT | 错误信息 |
| created_at | TIMESTAMP | 创建时间 |

#### 2.1.3 文章详情表 `article`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT FK | 关联配置ID |
| task_id | BIGINT FK | 关联任务ID |
| list_page_id | BIGINT FK | 关联列表页ID（回溯用） |
| title | VARCHAR(500) | 文章标题 |
| url | TEXT | 文章 URL |
| raw_html | TEXT | 原始 HTML |
| content | TEXT | 正文内容 |
| author | VARCHAR(100) | 作者 |
| publish_date | TIMESTAMP | 发布日期 |
| status | VARCHAR(20) | 状态：CRAWLED / FAILED |
| error_message | TEXT | 错误信息 |
| created_at | TIMESTAMP | 爬取时间 |

#### 2.1.4 爬取任务表 `crawl_task`

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

### 2.2 ER 关系

```
crawl_config (1) ──── (N) crawl_task
crawl_config (1) ──── (N) list_page
crawl_config (1) ──── (N) article
crawl_task (1) ──── (N) list_page
crawl_task (1) ──── (N) article
list_page (1) ──── (N) article
```

### 2.3 设计原则

- **list_page 单独存储**：列表页原始数据完整保留，出问题可以重新解析
- **article 通过 list_page_id 回溯**：方便定位数据来源
- **raw_html 字段**：保留原始内容，支持重新提取

---

## 3. 前端页面设计

### 3.1 页面结构

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

### 3.2 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/configs` | 配置管理页 | 列表展示所有配置 |
| `/configs/:id` | 配置详情/编辑页 | 查看、编辑、新建配置 |
| `/tasks` | 任务管理页 | 列表展示所有任务 |
| `/articles` | 数据展示页 | 表格展示文章数据 |

### 3.3 页面功能

#### 3.3.1 配置管理页 `/configs`
- 展示所有爬虫配置列表
- 支持新建、编辑、删除、测试配置
- 显示配置名称、类型、状态

#### 3.3.2 配置详情/编辑页 `/configs/:id`
- 配置名称、起始 URL、页面类型
- 选择器类型切换（CSS / XPath）
- 列表页规则配置（标题、URL、日期选择器）
- 详情页规则配置（标题、正文、作者选择器）
- 支持选择器测试（匹配示例）
- 保存配置 + 开始爬取按钮

#### 3.3.3 任务管理页 `/tasks`
- 展示所有爬取任务
- 显示任务进度（百分比、已爬/总数）
- 支持停止、删除任务
- 任务状态：运行中、已完成、已失败

#### 3.3.4 数据展示页 `/articles`
- 表格展示文章数据
- 支持关键词搜索、配置筛选、状态筛选
- 分页功能
- 导出功能（Excel/JSON）
- 文章详情抽屉展示

---

## 4. 后端 API 设计

### 4.1 API 基础路径

`/api/v1`

### 4.2 爬虫配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/configs` | 获取所有配置 |
| GET | `/configs/:id` | 获取单个配置 |
| POST | `/configs` | 创建配置 |
| PUT | `/configs/:id` | 更新配置 |
| DELETE | `/configs/:id` | 删除配置 |
| POST | `/configs/:id/test` | 测试配置（抓取示例） |

### 4.3 爬取任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tasks` | 获取所有任务 |
| GET | `/tasks/:id` | 获取任务详情 |
| POST | `/tasks` | 创建并启动任务 |
| POST | `/tasks/:id/stop` | 停止任务 |
| DELETE | `/tasks/:id` | 删除任务 |

### 4.4 列表页数据 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list-pages` | 获取列表页数据（分页） |
| GET | `/list-pages/:id` | 获取单条列表页 |
| POST | `/list-pages/:id/reparse` | 重新解析列表页 |
| DELETE | `/list-pages/:id` | 删除列表页 |

### 4.5 文章数据 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/articles` | 获取文章列表（分页+搜索） |
| GET | `/articles/:id` | 获取文章详情 |
| POST | `/articles/:id/reparse` | 重新解析文章 |
| DELETE | `/articles/:id` | 删除文章 |
| POST | `/articles/export` | 导出文章（Excel/JSON） |

---

## 5. 核心数据模型

### 5.1 Java 实体类

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
    private String pageType;        // LIST / DETAIL
    private String selectorType;    // CSS / XPATH
    private String listRules;       // JSON: {"title": ".title", "url": "a", "date": ".date"}
    private String detailRules;     // JSON: {"title": ".article-title", "content": ".content", "author": ".author"}
    private String detailUrlRule;
    private String status;          // ACTIVE / STOPPED
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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
    private String rawHtml;         // 保留原始HTML，用于回溯重做
    private String title;
    private String articleUrl;
    private LocalDateTime publishDate;
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
    private Long listPageId;        // 回溯用
    private String title;
    private String url;
    private String rawHtml;         // 原始HTML
    private String content;         // 清洗后的正文
    private String author;
    private LocalDateTime publishDate;
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

---

## 6. 爬虫引擎设计

### 6.1 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| HTTP 客户端 | Spring WebClient | 异步非阻塞 |
| HTML 解析 | Jsoup | 支持 CSS + XPath |
| 线程控制 | ThreadPoolExecutor | 传统线程池，支持任务并发控制 |
| 任务状态 | AtomicBoolean flag | 支持优雅停止 |

### 6.2 核心流程

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
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  3. 遍历 list_page → 检查停止 flag                        │
│     ┌──────────────────────────────────┐                   │
│     │  flag == false → 优雅退出         │                   │
│     │  flag == true → 继续爬取详情页    │                   │
│     └──────────────────────────────────┘                   │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  4. 爬取详情页 → 存入 article (status=CRAWLED/FAILED)    │
│     更新 list_page.status                                  │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│  5. 更新 Task 状态 (COMPLETED/FAILED)                     │
└────────────────────────────────────────────────────────────┘
```

### 6.3 核心类设计

#### CrawlService.java
- `startCrawl(configId, taskId)` - 启动爬取
- `stopCrawl(taskId)` - 停止爬取
- `crawlListPages(config, task)` - 爬取列表页
- `crawlDetailPages(config, task)` - 爬取详情页

#### SelectorService.java
- `extractListItems(html, config)` - 提取列表项
- `extractByCss(html, config)` - CSS 选择器提取
- `extractByXPath(html, config)` - XPath 选择器提取

---

## 7. 项目结构

### 7.1 前端项目结构 (Vue3)

```
frontend/
├── src/
│   ├── api/           # API 调用
│   │   ├── config.js
│   │   ├── task.js
│   │   └── article.js
│   ├── components/    # 公共组件
│   ├── pages/         # 页面
│   │   ├── ConfigList.vue
│   │   ├── ConfigEdit.vue
│   │   ├── TaskList.vue
│   │   └── ArticleList.vue
│   ├── router/
│   └── App.vue
├── package.json
└── vite.config.js
```

### 7.2 后端项目结构 (Spring Boot)

```
backend/
├── src/main/java/com/spider/
│   ├── controller/    # REST Controller
│   │   ├── ConfigController.java
│   │   ├── TaskController.java
│   │   └── ArticleController.java
│   ├── service/      # 业务逻辑
│   │   ├── CrawlService.java
│   │   └── SelectorService.java
│   ├── repository/   # 数据访问
│   ├── entity/       # 实体类
│   │   ├── CrawlConfig.java
│   │   ├── ListPage.java
│   │   ├── Article.java
│   │   └── CrawlTask.java
│   └── SpiderApplication.java
├── src/main/resources/
│   └── application.yml
├── pom.xml
└── ...
```

---

## 8. MVP 功能清单

### 8.1 配置管理
- [ ] 创建爬虫配置（名称、URL、页面类型）
- [ ] 选择器类型切换（CSS / XPath）
- [ ] 配置列表页规则
- [ ] 配置详情页规则
- [ ] 测试配置（预览提取结果）
- [ ] 编辑/删除配置

### 8.2 爬取任务
- [ ] 创建并启动爬取任务
- [ ] 查看任务进度
- [ ] 停止任务
- [ ] 删除任务

### 8.3 数据展示
- [ ] 表格展示文章列表
- [ ] 关键词搜索
- [ ] 配置筛选
- [ ] 状态筛选
- [ ] 分页
- [ ] 导出（Excel/JSON）
- [ ] 文章详情抽屉

### 8.4 回溯机制
- [ ] 查看列表页原始数据
- [ ] 重新解析列表页
- [ ] 查看文章原始HTML

---

## 9. MVP 与迭代规划

### 9.1 MVP 第一版（当前实现）

**核心功能：**
- 手动输入 CSS/XPath 选择器
- 配置 → 爬取 → 展示完整链路

**用户操作流程：**
1. 用户在配置页手动输入起始 URL
2. 手动输入 CSS 或 XPath 选择器规则
3. 启动爬取任务
4. 在数据展示页查看结果

### 9.2 第二版规划（后续迭代）

**可视化选择器功能：**

| 功能点 | 说明 |
|--------|------|
| 嵌入式浏览器 | 前端内嵌 Playwright，用户在页面内直接操作 |
| 点击生成选择器 | 用户点击页面元素，自动生成对应的 CSS/XPath |
| 模板支持 | 预设"新闻列表"等模板，快速填充字段 |
| 自定义映射 | 支持用户自定义字段与选择器的映射关系 |
| 前后端分离 | 前端控制浏览器，后端只存储配置 |

**技术方案：**
- 前端：Vue3 + Playwright（前端内嵌）
- 后端：仅负责存储选择器配置
- 通信：前端直接控制浏览器，选择器生成后发送到后端保存

---

## 10. 验收标准

1. ✅ 能够创建并保存爬虫配置（支持 CSS + XPath）
2. ✅ 能够启动爬取任务，实时查看进度
3. ✅ 能够停止正在运行的爬取任务
4. ✅ 爬取的数据正确存储到 PostgreSQL
5. ✅ 前端表格展示文章数据，支持搜索过滤
6. ✅ 列表页数据单独存储，支持回溯重做
7. ✅ 保留原始 HTML，支持重新解析
8. ✅ 任务可中断，优雅退出
