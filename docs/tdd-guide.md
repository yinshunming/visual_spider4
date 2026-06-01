# TDD 模板

## 哲学

**核心原则**：测试通过**公共接口**验证行为，不验证实现细节。代码可以完全重构，测试不应随之失效。

- **好测试** = 集成式风格：通过公共 API 走真实代码路径。读起来像规约。
- **坏测试** = 耦合到实现：mock 内部协作者、测私有方法、通过外部手段（直接查 DB）绕接口。
- **红旗**：重构时测试失败，但行为没变 → 那测试在测实现，不在测行为。

## 反模式：水平切片

**禁止**先写完所有测试再写所有实现。这是「RED = 写全部测试，GREEN = 写全部代码」。

产生 **坏测试**：
- 批量写的测试测的是**想象中的行为**而不是**真实行为**
- 测试变得对真实变化不敏感、对无关变化过度敏感
- 在没理解实现时锁定结构 → 失去反馈

**正确**：垂直切片（tracer bullet）。一次一个 RED→GREEN 循环。

```
WRONG (horizontal):
  RED:   test1, test2, test3, test4, test5
  GREEN: impl1, impl2, impl3, impl4, impl5

RIGHT (vertical):
  RED→GREEN: test1→impl1
  RED→GREEN: test2→impl2
  RED→GREEN: test3→impl3
  ...
```

## 工作流程

### 1. 规划

写代码前先问自己：
- [ ] 公共接口该怎么变？
- [ ] 哪些行为必须测？（**优先级**，不是所有边界）
- [ ] 哪些是 [deep modules](https://en.wikipedia.org/wiki/Deep_module)（小接口、深实现）？
- [ ] 接口是否便于测试？

> **你测不了所有东西**。和用户/团队对齐"哪些行为最重要"。

### 2. Tracer Bullet

```
RED:   写一个测试，验证一件事 → 测试失败
GREEN: 写最小代码让它通过 → 测试通过
```

这一步证明端到端的路径是通的。

### 3. 增量循环

对每个后续行为：

```
RED:   写下一个测试 → 失败
GREEN: 最小代码让它通过 → 通过
```

规则：
- 一次一个测试
- 只写让当前测试通过的代码
- **不要预写未来测试**
- 保持测试聚焦于可观察行为

### 4. 重构

所有测试通过后：
- [ ] 提取重复
- [ ] 加深模块（把复杂度藏在简单接口后）
- [ ] 应用 SOLID 原则
- [ ] 看新代码揭示了现有代码的什么

**永远不在 RED 状态重构**。先 GREEN。

## 每个循环的检查清单

```markdown
[ ] 测试描述行为，不描述实现
[ ] 测试只用公共接口
[ ] 测试在内部重构后会存活
[ ] 代码只为通过当前测试
[ ] 没有投机性特性
```

## 后端测试模板

### Service 单元测试（Mockito）

```java
@ExtendWith(MockitoExtension.class)
class CrawlConfigServiceTest {
    @Mock private CrawlConfigRepository repository;
    @InjectMocks private CrawlConfigService service;

    @Test
    @DisplayName("创建配置时 status 默认 STOPPED")
    void create_setsDefaultStatusToStopped() {
        // Arrange
        CrawlConfig input = new CrawlConfig();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CrawlConfig result = service.create(input);

        // Assert
        assertThat(result.getStatus()).isEqualTo(ConfigStatus.STOPPED);
    }
}
```

### Controller Web 测试（MockMvc）

```java
@WebMvcTest(ConfigController.class)
class ConfigControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CrawlConfigService service;

    @Test
    void getById_existing_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(buildConfig(1L));

        mockMvc.perform(get("/api/v1/configs/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }
}
```

### Repository 集成测试（DataJpaTest + 本地 PG）

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CrawlConfigRepositoryTest {
    @Autowired private CrawlConfigRepository repository;

    @Test
    void save_thenFindById_returnsSaved() {
        CrawlConfig saved = repository.save(buildConfig());
        assertThat(repository.findById(saved.getId())).isPresent();
    }
}
```

> 当前使用本机 PostgreSQL（详见 [runbook.md](runbook.md) Known Issues），不依赖 Testcontainers。

## 命名约定

- 方法名 `methodName_scenario_expectedBehavior()`
- `@DisplayName("人类可读描述")` 用于报告
- 行为场景用 `@Nested` 分组，如 `CrawlConfigServiceTest.Create` / `.GetById`

## 覆盖率目标

| 层级 | 目标 |
|------|------|
| Service | > 80% |
| Repository | > 70% |
| Controller | > 70% |

不重要：getter/setter、配置类、纯 POJO 构造函数。

## Mocking 准则

- ✅ mock 外部协作者（Repository → Service 测试中）
- ❌ mock 你自己写的内部类
- ❌ mock 静态方法、final 类
- ❌ 用 `verifyNoMoreInteractions` 过度断言

## 当前 M1 测试统计

- **37 项测试**全绿
- 分布：Repository 7 / Service 14 / Controller 11 / Exception 5
- 详见 [runbook.md](runbook.md) §Backend
