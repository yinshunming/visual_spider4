package com.visualspider.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.visualspider.entity.Article;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.entity.CrawlTask;
import com.visualspider.entity.ListItem;
import com.visualspider.entity.ListPage;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.ItemStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.enums.TaskStatus;
import com.visualspider.exception.TaskAlreadyRunningException;
import com.visualspider.repository.ArticleRepository;
import com.visualspider.repository.CrawlTaskRepository;
import com.visualspider.repository.DetailUrlRepository;
import com.visualspider.repository.ListItemRepository;
import com.visualspider.repository.ListPageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CrawlEngine 调度器测试(Section 7 LIST_DETAIL)。
 * 仅 mock Playwright Page / BrowserContext / Playwright(允许 mock 外部 SDK)。
 * 不 mock 内部 Bean — 使用真实 UrlGuard(其本身已在 UrlGuardTest 中独立验证)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlEngine")
class CrawlEngineTest {

    @Mock private Playwright playwright;
    @Mock private com.microsoft.playwright.Browser browser;
    @Mock private com.microsoft.playwright.BrowserType browserType;
    @Mock private BrowserContext browserContext;
    @Mock private Page page;

    @Mock private CrawlTaskRepository taskRepository;
    @Mock private ListPageRepository listPageRepository;
    @Mock private ListItemRepository listItemRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private DetailUrlRepository detailUrlRepository;
    @Mock private CrawlConfigService configService;

    private CrawlEngine engine;
    private MockedStatic<Playwright> playwrightStatic;

    @BeforeEach
    void setUp() {
        FieldValueValidator validator = new FieldValueValidator();
        ExtractionService extractionService = new ExtractionService(configService, validator);
        engine = new CrawlEngine(taskRepository, listPageRepository, listItemRepository,
                articleRepository, detailUrlRepository, configService, extractionService);
        org.mockito.Mockito.lenient().when(page.content()).thenReturn(
                "<html><body><a class='title' href='https://example.com/article/1'>A</a>"
                        + "<a class='title' href='https://example.com/article/2'>B</a></body></html>");
    }

    @AfterEach
    void tearDown() {
        if (playwrightStatic != null) {
            playwrightStatic.close();
        }
    }

    private CrawlConfig buildConfig(PageType type, String startUrl) {
        CrawlConfig config = new CrawlConfig();
        config.setId(1L);
        config.setName("cfg");
        config.setStartUrl(startUrl);
        config.setPageType(type);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);

        CrawlField listTitle = new CrawlField();
        listTitle.setConfig(config);
        listTitle.setPageType(FieldPageType.LIST);
        listTitle.setFieldName("title");
        listTitle.setFieldType(FieldType.TEXT);
        listTitle.setSelector(".title");
        config.getFields().add(listTitle);

        CrawlField listDetail = new CrawlField();
        listDetail.setConfig(config);
        listDetail.setPageType(FieldPageType.LIST);
        listDetail.setFieldName("detail_url");
        listDetail.setFieldType(FieldType.URL);
        listDetail.setSelector(".title");
        config.getFields().add(listDetail);

        CrawlField detailTitle = new CrawlField();
        detailTitle.setConfig(config);
        detailTitle.setPageType(FieldPageType.DETAIL);
        detailTitle.setFieldName("title");
        detailTitle.setFieldType(FieldType.TEXT);
        detailTitle.setSelector(".title");
        config.getFields().add(detailTitle);

        return config;
    }

    private CrawlTask buildTask(CrawlConfig config, TaskStatus status) {
        CrawlTask task = new CrawlTask();
        task.setId(10L);
        task.setConfig(config);
        task.setPageType(config.getPageType());
        task.setStatus(status);
        return task;
    }

    private void stubPlaywrightFactory() {
        playwrightStatic = mockStatic(Playwright.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        playwrightStatic.when(Playwright::create).thenReturn(playwright);
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launch()).thenReturn(browser);
        when(browser.newContext()).thenReturn(browserContext);
        when(browserContext.newPage()).thenReturn(page);
    }

    @Nested
    @DisplayName("§1 LIST_DETAIL - startUrl 校验失败")
    class StartUrlValidationFails {

        @Test
        @DisplayName("startUrl 为 ftp:// → task FAILED,error_message 含 startUrl")
        void invalidStartUrl_marksTaskFailed() {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "ftp://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);

            engine.run(task);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.getErrorMessage()).contains("startUrl");
            assertThat(task.getCompletedAt()).isNotNull();
            verify(taskRepository).save(task);
        }
    }

    @Nested
    @DisplayName("§2 LIST_DETAIL - 正常流程")
    class ListDetailHappyPath {

        @Test
        @DisplayName("正常流程:1 个 list_page + 2 个 list_item + 2 个 article,task COMPLETED")
        void happyPath_completesTask() {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);

            when(configService.getByIdWithFields(1L)).thenReturn(config);
            when(page.evaluate(any(), any())).thenAnswer(inv -> {
                String selector = inv.getArgument(1);
                // 模拟 ExtractionService 内部 JS 脚本的真实行为:
                // 对 <a class='title' href='...'> 会返回 href(已经是绝对 URL)
                if (".title".equals(selector)) {
                    return List.of("https://example.com/article/1", "https://example.com/article/2");
                }
                return new ArrayList<String>();
            });
            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listPageRepository.save(any(ListPage.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listItemRepository.save(any(ListItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getErrorMessage()).isNull();
            assertThat(task.getCompletedAt()).isNotNull();
            assertThat(task.getTotalItems()).isEqualTo(2);
            assertThat(task.getCrawledItems()).isEqualTo(2);
            assertThat(task.getFailedItems()).isZero();

            ArgumentCaptor<ListPage> pageCaptor = ArgumentCaptor.forClass(ListPage.class);
            verify(listPageRepository, times(1)).save(pageCaptor.capture());
            assertThat(pageCaptor.getValue().getUrl()).isEqualTo("https://example.com/list");
            assertThat(pageCaptor.getValue().getRawHtml()).contains("article/1");

            ArgumentCaptor<ListItem> itemCaptor = ArgumentCaptor.forClass(ListItem.class);
            verify(listItemRepository, times(4)).save(itemCaptor.capture());
            List<ListItem> items = itemCaptor.getAllValues();
            // 最后两次 save 是处理完后状态更新,断言这两次都是 CRAWLED
            List<ListItem> finalItems = items.subList(2, 4);
            assertThat(finalItems).extracting(ListItem::getDetailUrl)
                    .containsExactly("https://example.com/article/1", "https://example.com/article/2");
            assertThat(finalItems).allMatch(i -> i.getStatus() == ItemStatus.CRAWLED);

            ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
            verify(articleRepository, times(2)).save(articleCaptor.capture());
            List<Article> articles = articleCaptor.getAllValues();
            assertThat(articles).extracting(Article::getStatus).containsOnly(ItemStatus.CRAWLED);
            assertThat(articles).allMatch(a -> a.getCustomFields().containsKey("title"));
            assertThat(articles).noneMatch(a -> a.getCustomFields().containsKey("detail_url"));
        }
    }

    @Nested
    @DisplayName("§3 LIST_DETAIL - 单 article 异常隔离")
    class ArticleExceptionIsolation {

        @Test
        @DisplayName("中途某 article 抽取失败 → article FAILED + list_item 同步 FAILED,task 仍 COMPLETED")
        void midArticleFailure_isolatesAndTaskCompletes() {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);
            when(configService.getByIdWithFields(1L)).thenReturn(config);

            when(page.evaluate(any(), any())).thenAnswer(inv -> {
                String selector = inv.getArgument(1);
                if (".title".equals(selector)) {
                    return List.of("https://example.com/article/1", "https://example.com/article/2");
                }
                return new ArrayList<String>();
            });
            when(page.content()).thenReturn("<html></html>");
            // 3 次 navigate(startUrl、article/1、article/2):前 2 次成功,最后一次抛
            org.mockito.Mockito.doReturn(null)
                    .doReturn(null)
                    .doThrow(new RuntimeException("net::ERR_ABORTED"))
                    .when(page).navigate(anyString());

            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listPageRepository.save(any(ListPage.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listItemRepository.save(any(ListItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getCrawledItems()).isEqualTo(1);
            assertThat(task.getFailedItems()).isEqualTo(1);
            assertThat(task.getTotalItems()).isEqualTo(2);

            ArgumentCaptor<ListItem> itemCaptor = ArgumentCaptor.forClass(ListItem.class);
            verify(listItemRepository, times(4)).save(itemCaptor.capture());
            List<ListItem> items = itemCaptor.getAllValues();
            List<ListItem> finalItems = items.subList(2, 4);
            assertThat(finalItems.stream().filter(i -> i.getStatus() == ItemStatus.CRAWLED).count()).isEqualTo(1);
            ListItem failedItem = finalItems.stream().filter(i -> i.getStatus() == ItemStatus.FAILED).findFirst().orElseThrow();
            assertThat(failedItem.getErrorMessage()).contains("ERR_ABORTED");

            ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
            verify(articleRepository, times(2)).save(articleCaptor.capture());
            List<Article> articles = articleCaptor.getAllValues();
            assertThat(articles.stream().filter(a -> a.getStatus() == ItemStatus.FAILED).count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("§4 全局锁")
    class GlobalLock {

        @Test
        @DisplayName("已有任务在跑时,新任务抛 TaskAlreadyRunningException")
        void concurrentTasks_secondOneRejected() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "https://example.com/list");
            CrawlTask task1 = buildTask(config, TaskStatus.RUNNING);
            task1.setId(1L);

            Field lockField = CrawlEngine.class.getDeclaredField("runningTask");
            lockField.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<CrawlTask> ref = (AtomicReference<CrawlTask>) lockField.get(engine);
            ref.set(task1);

            CrawlTask task2 = buildTask(config, TaskStatus.RUNNING);
            task2.setId(2L);

            assertThatThrownBy(() -> engine.run(task2))
                    .isInstanceOf(TaskAlreadyRunningException.class);
            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("任务跑完后锁释放")
        void lockReleasedAfterRun() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);
            when(configService.getByIdWithFields(1L)).thenReturn(config);
            when(page.evaluate(any(), any())).thenReturn(new ArrayList<String>());
            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listPageRepository.save(any(ListPage.class))).thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            Field lockField = CrawlEngine.class.getDeclaredField("runningTask");
            lockField.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<CrawlTask> ref = (AtomicReference<CrawlTask>) lockField.get(engine);
            assertThat(ref.get()).isNull();
        }
    }

    @Nested
    @DisplayName("§5 DETAIL_ONLY 流程")
    class DetailOnly {

        @Test
        @DisplayName("DETAIL_ONLY + 3 个 URL → 3 篇 article + 3 个 detail_url 变 CRAWLED,task COMPLETED")
        void detailOnly_threeUrlsAllSucceed() {
            CrawlConfig config = buildConfig(PageType.DETAIL_ONLY, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);
            when(configService.getByIdWithFields(1L)).thenReturn(config);

            List<com.visualspider.entity.DetailUrl> urls = List.of(
                    detailUrl("https://example.com/a"),
                    detailUrl("https://example.com/b"),
                    detailUrl("https://example.com/c"));
            when(detailUrlRepository.findByTaskId(10L)).thenReturn(urls);

            when(page.evaluate(any(), any())).thenAnswer(inv -> List.of("title-value"));
            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
            when(detailUrlRepository.save(any(com.visualspider.entity.DetailUrl.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getTotalItems()).isEqualTo(3);
            assertThat(task.getCrawledItems()).isEqualTo(3);
            assertThat(task.getFailedItems()).isZero();

            ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
            verify(articleRepository, times(3)).save(articleCaptor.capture());
            assertThat(articleCaptor.getAllValues()).extracting(Article::getStatus)
                    .containsOnly(ItemStatus.CRAWLED);

            ArgumentCaptor<com.visualspider.entity.DetailUrl> urlCaptor =
                    ArgumentCaptor.forClass(com.visualspider.entity.DetailUrl.class);
            verify(detailUrlRepository, times(3)).save(urlCaptor.capture());
            assertThat(urlCaptor.getAllValues())
                    .extracting(com.visualspider.entity.DetailUrl::getStatus)
                    .containsOnly(com.visualspider.enums.DetailUrlStatus.CRAWLED);
        }

        @Test
        @DisplayName("DETAIL_ONLY 第 1 个 URL 抽取失败 → 该 article FAILED + detail_url FAILED,task 仍 COMPLETED")
        void detailOnly_oneUrlFails() {
            CrawlConfig config = buildConfig(PageType.DETAIL_ONLY, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);
            when(configService.getByIdWithFields(1L)).thenReturn(config);

            List<com.visualspider.entity.DetailUrl> urls = List.of(
                    detailUrl("https://example.com/a"),
                    detailUrl("https://example.com/b"));
            when(detailUrlRepository.findByTaskId(10L)).thenReturn(urls);

            when(page.evaluate(any(), any())).thenAnswer(inv -> List.of("title-value"));
            // 第 1 次 navigate(对 a)抛异常,第 2 次正常
            org.mockito.Mockito.doThrow(new RuntimeException("net::ERR_FAILED"))
                    .doReturn(null)
                    .when(page).navigate(anyString());
            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
            when(detailUrlRepository.save(any(com.visualspider.entity.DetailUrl.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getTotalItems()).isEqualTo(2);
            assertThat(task.getCrawledItems()).isEqualTo(1);
            assertThat(task.getFailedItems()).isEqualTo(1);

            ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
            verify(articleRepository, times(2)).save(articleCaptor.capture());
            Article failedArticle = articleCaptor.getAllValues().stream()
                    .filter(a -> a.getStatus() == ItemStatus.FAILED).findFirst().orElseThrow();
            assertThat(failedArticle.getErrorMessage()).contains("ERR_FAILED");
            assertThat(failedArticle.getUrl()).isEqualTo("https://example.com/a");
        }

        private com.visualspider.entity.DetailUrl detailUrl(String url) {
            com.visualspider.entity.DetailUrl d = new com.visualspider.entity.DetailUrl();
            d.setTask(new CrawlTask());
            d.setUrl(url);
            d.setStatus(com.visualspider.enums.DetailUrlStatus.PENDING);
            return d;
        }
    }

    @Nested
    @DisplayName("§6 停止机制")
    class StopMechanism {

        @Test
        @DisplayName("stop(taskId) 设置 stopFlag → 任务循环检测后退出,剩余 list_item 保持 PENDING,task=COMPLETED")
        void stop_midwayLeavesRemainderPending() {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL, "https://example.com/list");
            CrawlTask task = buildTask(config, TaskStatus.RUNNING);
            when(configService.getByIdWithFields(1L)).thenReturn(config);

            // LIST 抽取返回 3 个 URL,detail navigate 都正常(不抛)
            when(page.evaluate(any(), any())).thenAnswer(inv -> {
                String selector = inv.getArgument(1);
                if (".title".equals(selector)) {
                    return List.of("https://example.com/a",
                            "https://example.com/b",
                            "https://example.com/c");
                }
                return new ArrayList<String>();
            });
            when(page.content()).thenReturn("<html></html>");

            // 第 1 个 list_item 处理完之后发 stop,后续 list_item 不再 navigate
            final int[] navigateCount = {0};
            org.mockito.Mockito.doAnswer(inv -> {
                navigateCount[0]++;
                // 在第 2 次 navigate 之后发 stop(此时第 1 个 list_item 已完成,即将处理第 2 个)
                if (navigateCount[0] == 2) {
                    engine.stop(10L);
                }
                return null;
            }).when(page).navigate(anyString());

            stubPlaywrightFactory();
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listPageRepository.save(any(ListPage.class))).thenAnswer(inv -> inv.getArgument(0));
            when(listItemRepository.save(any(ListItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

            engine.run(task);

            // task COMPLETED(部分成功),crawled + failed < total
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getTotalItems()).isEqualTo(3);
            assertThat(task.getCrawledItems() + task.getFailedItems()).isLessThan(3);

            // 3 个 list_item 被创建(PENDING),只有 1 个被处理(CRAWLED 更新)
            // 总 save 次数 = 3(创建) + 1(更新已处理项) = 4
            verify(listItemRepository, org.mockito.Mockito.atMost(4)).save(any());
            // article 只为已处理的 list_item 创建 = 1 个
            verify(articleRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("stop 对未运行任务(taskId 不在 lock 中)抛 TaskNotFoundException")
        void stop_unrunningTask_throws() {
            assertThatThrownBy(() -> engine.stop(999L))
                    .isInstanceOf(com.visualspider.exception.TaskNotFoundException.class);
        }
    }
}