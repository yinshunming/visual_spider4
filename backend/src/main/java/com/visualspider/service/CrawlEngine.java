package com.visualspider.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.visualspider.dto.response.ExtractionPreviewResponse;
import com.visualspider.dto.response.FieldPreviewResult;
import com.visualspider.entity.Article;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlTask;
import com.visualspider.entity.DetailUrl;
import com.visualspider.entity.ListItem;
import com.visualspider.entity.ListPage;
import com.visualspider.enums.DetailUrlStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.ItemStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.TaskStatus;
import com.visualspider.exception.TaskAlreadyRunningException;
import com.visualspider.repository.ArticleRepository;
import com.visualspider.repository.CrawlTaskRepository;
import com.visualspider.repository.DetailUrlRepository;
import com.visualspider.repository.ListItemRepository;
import com.visualspider.repository.ListPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 爬取调度器(M4 引入):
 * - 全局单任务锁(进程内 AtomicReference)
 * - 自管 Playwright 生命周期(任务期间独占 BrowserContext)
 * - 复用 M3 ExtractionService 抽取内核
 * - LIST_DETAIL / DETAIL_ONLY 两种流程
 */
@Service
public class CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(CrawlEngine.class);
    private static final String DETAIL_URL_FIELD_NAME = "detail_url";

    private final CrawlTaskRepository taskRepository;
    private final ListPageRepository listPageRepository;
    private final ListItemRepository listItemRepository;
    private final ArticleRepository articleRepository;
    private final DetailUrlRepository detailUrlRepository;
    private final CrawlConfigService configService;
    private final ExtractionService extractionService;

    private final AtomicReference<CrawlTask> runningTask = new AtomicReference<>();
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    @Autowired
    public CrawlEngine(CrawlTaskRepository taskRepository,
                       ListPageRepository listPageRepository,
                       ListItemRepository listItemRepository,
                       ArticleRepository articleRepository,
                       DetailUrlRepository detailUrlRepository,
                       CrawlConfigService configService,
                       @Lazy ExtractionService extractionService) {
        this.taskRepository = taskRepository;
        this.listPageRepository = listPageRepository;
        this.listItemRepository = listItemRepository;
        this.articleRepository = articleRepository;
        this.detailUrlRepository = detailUrlRepository;
        this.configService = configService;
        this.extractionService = extractionService;
    }

    /**
     * 提交任务到调度器。立刻拿到锁就执行,执行结束(无论结果)释放锁。
     * 调用方应当在调用前先在事务内持久化 RUNNING 任务。
     */
    public void run(CrawlTask task) {
        if (!runningTask.compareAndSet(null, task)) {
            throw new TaskAlreadyRunningException();
        }
        stopFlag.set(false);
        try {
            runInternal(task);
        } finally {
            task.setCompletedAt(Instant.now());
            try {
                taskRepository.save(task);
            } catch (Exception e) {
                log.warn("保存任务终态失败: id={}", task.getId(), e);
            }
            runningTask.set(null);
            stopFlag.set(false);
        }
    }

    private void runInternal(CrawlTask task) {
        CrawlConfig config = task.getConfig();
        String startUrl = config.getStartUrl();
        try {
            UrlGuard.validate(startUrl, "startUrl");
        } catch (RuntimeException e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            return;
        }

        try (Playwright pw = Playwright.create();
             com.microsoft.playwright.Browser browser = pw.chromium().launch();
             BrowserContext ctx = browser.newContext();
             Page page = ctx.newPage()) {

            if (config.getPageType() == PageType.LIST_DETAIL) {
                runListDetail(task, page, startUrl);
            } else {
                runDetailOnly(task, page);
            }
        } catch (RuntimeException e) {
            log.error("CrawlEngine 顶层异常: taskId={}", task.getId(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(truncate(e.getMessage(), 1024));
            return;
        }

        // 顶层流程走完且非 FAILED → COMPLETED(部分成功 = COMPLETED)
        if (task.getStatus() != TaskStatus.FAILED) {
            task.setStatus(TaskStatus.COMPLETED);
        }
    }

    // --- LIST_DETAIL ---

    private void runListDetail(CrawlTask task, Page page, String startUrl) {
        // 1. 抓列表页
        page.navigate(startUrl);
        String rawHtml = page.content();

        ListPage listPage = new ListPage();
        listPage.setTask(task);
        listPage.setConfig(task.getConfig());
        listPage.setUrl(startUrl);
        listPage.setRawHtml(rawHtml);
        listPageRepository.save(listPage);

        // 2. LIST 模板抽取 detail_url
        ExtractionPreviewResponse listPreview = extractionService.extractByTemplate(
                page, task.getConfig().getId(), FieldPageType.LIST);
        List<String> detailUrls = extractDetailUrls(listPreview.fields());

        // 3. 建 list_item(PENDING) + task.totalItems
        List<ListItem> items = new ArrayList<>(detailUrls.size());
        for (String url : detailUrls) {
            ListItem item = new ListItem();
            item.setListPage(listPage);
            item.setDetailUrl(url);
            item.setStatus(ItemStatus.PENDING);
            items.add(listItemRepository.save(item));
        }
        task.setTotalItems(items.size());

        // 4. 逐条访问详情页
        int crawled = 0;
        int failed = 0;
        for (ListItem item : items) {
            if (stopFlag.get()) {
                break;
            }
            Article article = new Article();
            article.setTask(task);
            article.setConfig(task.getConfig());
            article.setListItem(item);
            article.setUrl(item.getDetailUrl());

            String itemError = null;
            Map<String, String> customFields = new LinkedHashMap<>();
            try {
                page.navigate(item.getDetailUrl());
                article.setRawHtml(page.content());
                ExtractionPreviewResponse detailPreview = extractionService.extractByTemplate(
                        page, task.getConfig().getId(), FieldPageType.DETAIL);
                for (FieldPreviewResult fr : detailPreview.fields()) {
                    if (DETAIL_URL_FIELD_NAME.equals(fr.fieldName())) {
                        continue;
                    }
                    if (fr.validatedValues() != null && !fr.validatedValues().isEmpty()) {
                        customFields.put(fr.fieldName(), fr.validatedValues().get(0));
                    }
                }
                article.setCustomFields(customFields);
                article.setStatus(ItemStatus.CRAWLED);
                item.setStatus(ItemStatus.CRAWLED);
                crawled++;
            } catch (RuntimeException e) {
                itemError = truncate(e.getMessage(), 1024);
                article.setErrorMessage(itemError);
                article.setStatus(ItemStatus.FAILED);
                item.setStatus(ItemStatus.FAILED);
                item.setErrorMessage(itemError);
                failed++;
            }
            articleRepository.save(article);
            listItemRepository.save(item);
        }
        task.setCrawledItems(crawled);
        task.setFailedItems(failed);
    }

    private List<String> extractDetailUrls(List<FieldPreviewResult> fields) {
        for (FieldPreviewResult fr : fields) {
            if (DETAIL_URL_FIELD_NAME.equals(fr.fieldName())
                    && fr.fieldType() == FieldType.URL
                    && fr.validatedValues() != null
                    && !fr.validatedValues().isEmpty()) {
                return fr.validatedValues();
            }
        }
        return List.of();
    }

    // --- DETAIL_ONLY ---

    private void runDetailOnly(CrawlTask task, Page page) {
        List<DetailUrl> detailUrls = detailUrlRepository.findByTaskId(task.getId());
        task.setTotalItems(detailUrls.size());

        int crawled = 0;
        int failed = 0;
        for (DetailUrl du : detailUrls) {
            if (stopFlag.get()) {
                break;
            }
            Article article = new Article();
            article.setTask(task);
            article.setConfig(task.getConfig());
            article.setDetailUrl(du);
            article.setUrl(du.getUrl());

            String errMsg = null;
            Map<String, String> customFields = new LinkedHashMap<>();
            try {
                page.navigate(du.getUrl());
                article.setRawHtml(page.content());
                ExtractionPreviewResponse detailPreview = extractionService.extractByTemplate(
                        page, task.getConfig().getId(), FieldPageType.DETAIL);
                for (FieldPreviewResult fr : detailPreview.fields()) {
                    if (DETAIL_URL_FIELD_NAME.equals(fr.fieldName())) {
                        continue;
                    }
                    if (fr.validatedValues() != null && !fr.validatedValues().isEmpty()) {
                        customFields.put(fr.fieldName(), fr.validatedValues().get(0));
                    }
                }
                article.setCustomFields(customFields);
                article.setStatus(ItemStatus.CRAWLED);
                du.setStatus(DetailUrlStatus.CRAWLED);
                crawled++;
            } catch (RuntimeException e) {
                errMsg = truncate(e.getMessage(), 1024);
                article.setErrorMessage(errMsg);
                article.setStatus(ItemStatus.FAILED);
                du.setStatus(DetailUrlStatus.FAILED);
                du.setErrorMessage(errMsg);
                failed++;
            }
            articleRepository.save(article);
            detailUrlRepository.save(du);
        }
        task.setCrawledItems(crawled);
        task.setFailedItems(failed);
    }

    // --- 停止 ---

    public void stop(Long taskId) {
        CrawlTask current = runningTask.get();
        if (current == null || !current.getId().equals(taskId)) {
            throw new com.visualspider.exception.TaskNotFoundException(taskId);
        }
        stopFlag.set(true);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}