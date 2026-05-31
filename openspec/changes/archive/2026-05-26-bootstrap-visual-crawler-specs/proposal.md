# Proposal: bootstrap-visual-crawler-specs

## Why

当前 `docs/superpowers/specs/2026-05-24-nba-spider-design.md` 是可视化爬虫 MVP 的唯一设计文档，散落在 docs 目录中未结构化。主要问题：

1. **缺乏结构化的需求契约**：设计文档以自然语言描述，缺少明确的 capability 边界和验收标准，导致评审和实现之间容易出现理解偏差
2. **无法追踪需求变更**：随着实现推进，需求调整没有统一出口，容易出现文档与实现不一致
3. **知识无法复用**：爬虫配置、会话管理、数据模型等核心能力没有抽象为可复用的 spec，后续迭代缺乏一致性和评审基准

本提案将设计文档的内容转化为 OpenSpec specs 体系，使设计文档成为 **真相源（source of truth）**，支撑后续实现、评审和归档全流程。

## What Changes

1. **初始化 specs 目录结构**：在 `openspec/specs/` 下创建本项目所有 capability 的 spec.md 文件
2. **定义 Capability 边界**：将设计文档拆解为独立 spec，明确输入/输出/行为约束
3. **建立 delta spec 机制**：后续需求变更通过 delta spec 管理，避免直接修改已归档的 spec
4. **关联 change → specs → tasks**：所有开发任务直接承接 spec 中的 requirement，所有 artifact 追溯到同一变更源

## Capabilities

### New Capabilities

- `crawl-config`: 爬虫配置管理——用户创建、编辑、删除爬虫配置（名称、起始URL、页面类型、选择器类型）；配置与字段解耦
- `crawl-field`: 自定义字段映射——用户定义字段名（自由文本）+ 字段类型（TEXT/NUMBER/DATE/URL）+ 选择器（CSS/XPath）；字段分 LIST/DETAIL 两种页面类型
- `crawl-execution`: 爬取执行引擎——支持 LIST_DETAIL（列表页→多个列表项→详情页）和 DETAIL_ONLY（URL列表→直接详情）两种模式；AtomicBoolean 支持优雅停止
- `browser-session`: 浏览器会话管理——本地 Spring Boot 进程直接启动 Playwright 单会话；提供 open/click/extract/close 四个原子操作
- `browser-streaming`: 浏览器画面推送——WebSocket 实时推送截图帧、页面加载状态和错误信息到前端
- `selector-generation`: 选择器生成——点击页面元素自动生成 CSS/XPath 选择器；支持手动补充和切换
- `data-reparse`: 数据回溯重解析——raw_html 保留完整原始页面；支持对 list_page 和 article 重新解析
- `data-export`: 数据导出——文章数据支持 Excel/JSON 格式导出；custom_fields 动态列展开展示

### Modified Capabilities

- （空——这是 bootstrap 变更，specs 目录为空，无历史 capability 可修改）

## Impact

| 区域 | 影响 |
|------|------|
| **文档结构** | `openspec/specs/` 下新增 8 个 capability spec 文件；`openspec/changes/bootstrap-visual-crawler-specs/` 作为 bootstrap change 归档 |
| **开发流程** | 后续所有实现任务必须承接 spec 中的 requirement；change → specs → tasks 链路打通 |
| **评审基准** | Spec 即评审契约；实现与 spec 不一致视为未完成 |
| **技术决策** | 以下决策已在设计文档中明确，将在 spec 中正式固化：<br>• 后端 Playwright 单会话（不复用浏览器池）<br>• custom_fields 使用 JSONB 存储<br>• raw_html 完整保留用于回溯<br>• CSS/XPath 二选一（不同时使用） |

## Open Questions & Risks

| 类别 | 问题 | 风险等级 | 说明 |
|------|------|----------|------|
| **架构** | browser-session 的"单会话"约束是 MVP 临时限制还是长期设计？ | ~~中~~ **已确认** | **MVP 临时限制**。单会话简化浏览器生命周期管理；后续迭代可通过会话池支持多标签页并发爬取，届时 spec 需重构。 |
| **数据模型** | custom_fields JSONB 存储导致无法按单个字段索引查询，是否满足数据展示页的搜索需求？ | ~~中~~ **已确认** | **接受现状，按全量导出处理**。MVP 阶段通过应用层过滤；大规模数据时再考虑全文索引或独立字段列方案。 |
| **字段校验** | 字段类型（TEXT/NUMBER/DATE/URL）在爬取时如何校验？失败是跳过该字段还是标记整条数据 FAILED？ | 低 | 需在 spec 中明确 extracted field 的 error 语义。初步建议：字段解析失败时该字段留空，整条记录仍入库，status 标记 WARNED（不等同 FAILED）。 |
| **选择器鲁棒性** | 点击生成的 CSS/XPath 选择器在页面结构微调后可能失效，MVP 是否需要选择器测试/验证机制？ | 低 | 属于后续迭代范围（选择器模板），但需在 spec 中注明 |
| **反爬边界** | MVP 声明不含反爬策略，但真实网站普遍有基础反爬，实际可用性存疑 | ~~中~~ **已确认** | **先不管反爬问题**，后续逐步考虑。MVP 目标网站为无反爬或低反爬场景（如新闻、博客等公开页面）。 |
| **前端状态管理** | 设计文档提到 Pinia Store 划分（config/task/article），但未定义 store 之间的共享协议 | 低 | 属于实现细节，可在 tasks 阶段细化。初步原则：配置变更不自动刷新任务列表，需手动刷新或订阅。 |

## Schema

本 change 使用 `spec-driven` schema，artifact 顺序：

```
proposal → design → specs → tasks
```

- **proposal**（本 artifact）：建立 WHY 和 capability 边界
- **design**：沉淀设计文档中的技术决策（架构、数据模型、API 契约）
- **specs**：为每个 capability 编写正式的 spec.md（输入/输出/行为/约束）
- **tasks**：从 spec 拆解出可执行的任务清单
