# AGENTS.md - 可视化爬虫 MVP

**项目**: visual_spider4 | **阶段**: 初始化中 | **技术栈**: Vue3/Vite/Element Plus/Pinia + Spring Boot 3.x/JPA + PostgreSQL + Playwright + WebSocket

---

## 1. 项目阶段说明

项目已完成 OpenSpec specs bootstrap，8 个 capability specs 已同步到 `openspec/specs/` 作为开发真相源。原始设计文档归档于 `openspec/changes/archive/2026-05-26-bootstrap-visual-crawler-specs/`。

**当前状态**：
- `frontend/` - ✅ 已创建（基础工程：Vue 3 + Vite + Element Plus）
- `backend/` - ✅ 已创建（基础工程：Spring Boot 3.2.5 + 健康检查端点）
- OpenSpec specs — ✅ 已同步（8 个 capabilities）
- 数据库表结构 — ✅ 数据库 `visual_spider4` 已创建，schema 待实现

---

## 2. 前端技术栈

| 分层 | 技术 | 说明 |
|------|------|------|
| 框架 | Vue 3 + Vite | 使用 `<script setup>` 语法 |
| UI 库 | Element Plus | 组件库 |
| 状态管理 | Pinia | Store 划分见下方 |
| HTTP | Axios | 统一封装，baseURL `/api/v1` |
| 实时通道 | WebSocket | 接收后端推送的浏览器截图帧、加载状态和错误信息 |

**Pinia Store 划分**：
| Store | 职责 |
|-------|------|
| `useConfigStore` | 爬虫配置 CRUD |
| `useTaskStore` | 任务管理 |
| `useArticleStore` | 文章数据 |

**页面路由**（待实现）：
| 路径 | 页面 |
|------|------|
| `/configs` | 配置管理列表 |
| `/configs/:id` | 配置编辑 |
| `/tasks` | 任务管理 |
| `/articles` | 数据展示 |

---

## 3. 后端技术栈

| 分层 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.x | JPA + Spring Data |
| 数据库 | PostgreSQL | 关系型数据库 |
| 动态页面引擎 | Playwright | 本地 Spring Boot 进程直接启动真实浏览器，等待 JS 渲染，支持 CSS/XPath |
| 静态 HTML 解析 | Jsoup | 仅用于 raw_html 重新解析或简单静态页面 |
| 实时通道 | WebSocket | 推送浏览器截图帧、加载状态和错误信息 |
| HTTP 客户端 | Spring WebClient | 辅助静态请求，不作为动态页面主爬取路径 |

**分层架构**：
```
Controller -> Service -> Repository -> Entity
```

**包结构**：
```
com.visualspider
├── controller/    # REST API
├── websocket/     # 浏览器画面和状态 WebSocket 推送
├── service/      # 业务逻辑
├── repository/   # JPA Repository
├── entity/       # JPA Entity
├── dto/          # Data Transfer Object
└── exception/    # 统一异常处理
```

**统一响应格式**：
```json
// 成功
{ "code": 200, "data": {...}, "message": "success" }

// 错误
{ "code": 400, "data": null, "message": "错误描述" }
```

**异常处理**：
- 使用 `@ControllerAdvice` 全局处理
- 自定义异常：`BusinessException`
- HTTP 状态码：200 成功 / 400 参数错误 / 500 系统错误

---

## 4. 数据库设计原则

**核心表**（待创建）：
| 表名 | 说明 |
|------|------|
| `crawl_config` | 爬虫配置 |
| `crawl_field` | 字段配置 |
| `list_page` | 列表页数据（保留 raw_html 支持回溯） |
| `list_item` | 列表项数据（detail_url + 列表页字段） |
| `article` | 文章详情 |
| `crawl_task` | 爬取任务 |
| `detail_url` | DETAIL_ONLY 模式下的直接详情页 URL |

**设计原则**：
- `list_page` 单独存储，保留 `raw_html` 原始数据，出问题可重新解析
- `list_item` 存储列表中的每条记录，包含 `detail_url` 和列表页自定义字段
- `article` 通过 `list_item_id` 回溯列表项来源；DETAIL_ONLY 模式下 `list_item_id` 为空
- `status` 统一 `VARCHAR(20)`
- `created_at/updated_at` 使用 `TIMESTAMP`
- HTML 字段使用 `TEXT`

---

## 5. API 设计原则

**基础路径**: `/api/v1`

**核心端点**（待实现）：

| 资源 | 端点 | 说明 |
|------|------|------|
| 爬虫配置 | `/configs` | CRUD + 测试 |
| 爬取任务 | `/tasks` | 创建/启动/停止/删除 |
| 文章数据 | `/articles` | 分页查询 + 导出 |
| 列表页数据 | `/list-pages` | 回溯重新解析 |
| 列表项数据 | `/list-items` | 分页查询列表项 |
| 浏览器会话 | `/browser/open`, `/browser/click`, `/browser/extract`, `/browser/close` | 后端 Playwright 控制动作 |
| 浏览器实时通道 | `/ws/browser` | WebSocket 推送浏览器截图帧和状态 |

---

## 6. 爬虫引擎要点

| 组件 | 技术 | 说明 |
|------|------|------|
| 动态页面引擎 | Playwright | 主爬取路径；后端本地启动真实浏览器并等待 JS 渲染 |
| 选择器 | CSS + XPath | MVP 同时支持 |
| 浏览器画面 | WebSocket | 推送截图帧、页面加载状态和错误信息 |
| 静态 HTML 解析 | Jsoup | raw_html 重新解析或简单静态页面辅助 |
| HTTP 客户端 | Spring WebClient | 静态请求辅助，不替代 Playwright 动态页面能力 |
| 线程控制 | ThreadPoolExecutor | 并发控制 |
| 优雅停止 | AtomicBoolean | 捕获中断信号，逐步退出 |

**浏览器会话约束**：MVP 为单用户单 Playwright 浏览器会话；重复打开页面时复用当前会话或先关闭旧会话。

---

## 7. 开发命令

### 前端
```bash
cd frontend
npm install
npm run dev      # 开发服务器
npm run build    # 构建
npm run preview  # 预览构建
```

### 后端
```bash
cd backend
mvn clean compile   # 编译
mvn test           # 测试
mvn spring-boot:run # 启动应用
```

### 验证顺序
```bash
lint -> typecheck -> test -> build
```

---

## 8. Git 规范

**分支命名**：
```
feature/{功能名}
fix/{问题描述}
refactor/{重构内容}
```

**Commit Message**：
```
{type}: {简短描述}

type: feat | fix | refactor | test | docs | chore
```

---

## 9. 代码风格

**Java**：
- 类名：PascalCase（如 `CrawlConfigController`）
- 方法名/变量名：camelCase
- 表名/字段名：snake_case

**JavaScript/Vue**：
- 组件文件：PascalCase（如 `ConfigList.vue`）
- 使用 Vue3 `<script setup>` 语法
- Props 使用 `defineProps`，响应式使用 `ref/reactive`

---

## 10. TDD 开发模式

**核心原则**：测试通过公共接口验证行为，不验证实现细节。代码可以完全重构，测试不应随之失效。

### 10.1 工作流程

```
RED:   写一个测试 → 测试失败
GREEN: 写最小代码通过测试 → 测试通过
REFACTOR: 重构 → 测试保持绿色
```

**垂直切片方式**（正确）：
```
RED→GREEN: test1 → impl1
RED→GREEN: test2 → impl2
RED→GREEN: test3 → impl3
...
```

**禁止水平切片**（错误）：
```
RED:   一次写完所有测试
GREEN: 一次写完所有实现
```

### 10.2 测试分层

| 层级 | 工具 | 说明 |
|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 业务逻辑 |
| Web 层测试 | MockMvc | Controller 接口 |
| 集成测试 | SpringBootTest | 端到端验证 |
| 持久层测试 | DataJpaTest | Repository |

### 10.3 后端测试模板

**单元测试（Service）**：
```java
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {
    @Mock XxxRepository repo;
    @InjectMocks XxxService service;

    @Test
    void should_xxx_when_xxx() {
        // Arrange - 准备测试数据
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act - 执行被测方法
        Xxx result = service.xxx();

        // Assert - 验证结果
        assertThat(result.getXxx()).isEqualTo("expected");
        verify(repo).save(any());
    }
}
```

**Web 层测试（Controller）**：
```java
@WebMvcTest(XxxController.class)
class XxxControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean XxxService xxxService;

    @Test
    void should_return_xxx_when_get() throws Exception {
        when(xxxService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/xxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }
}
```

**集成测试**：
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XxxIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void should_create_xxx() throws Exception {
        mockMvc.perform(post("/api/v1/xxx")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
              {"name":"Test","type":"LIST_DETAIL"}
              """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("Test"));
    }
}
```

### 10.4 测试覆盖率目标

| 层级 | 目标覆盖率 |
|------|-----------|
| Service | >80% |
| Repository | >70% |
| Controller | >70% |

### 10.5 测试数据构建器

```java
class CrawlConfigBuilder {
    private String name = "测试配置";
    private String pageType = "LIST_DETAIL";
    private String startUrl = "https://example.com";

    CrawlConfigBuilder withName(String name) { this.name = name; return this; }
    CrawlConfigBuilder withPageType(String type) { this.pageType = type; return this; }
    CrawlConfig build() { return new CrawlConfig(null, name, pageType, startUrl, "ACTIVE"); }
}
```

### 10.6 Testcontainers（集成测试）

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
class XxxRepositoryTest {
    // 使用真实 PostgreSQL 容器进行测试
}
```

### 10.7 验证命令

```bash
# 后端完整验证
cd backend
mvn clean test          # 运行所有测试
mvn test jacoco:report   # 生成覆盖率报告

# 验证覆盖率达标
mvn verify
```

---

## 11. OpenSpec Specs 真相源

所有开发需求、评审和实现追踪以 `openspec/specs/` 下的 specs 为准：

| Capability | 说明 |
|-----------|------|
| `project-management` | 项目配置生命周期、页面类型模式、状态 |
| `page-visual-selection` | 浏览器会话、截图流、点击生成选择器 |
| `selector-rule-management` | 字段定义、选择器类型约束、detail_url 必填 |
| `extraction-template` | 抽取模板、DOM提取、多值、类型校验、重解析 |
| `crawl-execution` | 任务生命周期、爬取流程、优雅停止 |
| `extraction-preview-validation` | 实时预览、字段提取、部分成功 |
| `data-persistence` | raw_html保留、custom_fields JSON、查询导出 |
| `system-boundaries` | MVP约束（单会话/单用户/无认证/无反爬）|

归档记录：`openspec/changes/archive/2026-05-26-bootstrap-visual-crawler-specs/`
