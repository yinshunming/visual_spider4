package com.visualspider.service;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlTask;
import com.visualspider.entity.DetailUrl;
import com.visualspider.enums.DetailUrlStatus;
import com.visualspider.enums.TaskStatus;
import com.visualspider.exception.TaskNotFoundException;
import com.visualspider.repository.ArticleRepository;
import com.visualspider.repository.CrawlTaskRepository;
import com.visualspider.repository.DetailUrlRepository;
import com.visualspider.repository.ListItemRepository;
import com.visualspider.repository.ListPageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 任务 CRUD。不调度执行 — 调度由 {@link CrawlEngine} 负责。
 */
@Service
public class CrawlTaskService {

    private final CrawlConfigService configService;
    private final CrawlTaskRepository taskRepository;
    private final ListPageRepository listPageRepository;
    private final ListItemRepository listItemRepository;
    private final ArticleRepository articleRepository;
    private final DetailUrlRepository detailUrlRepository;

    public CrawlTaskService(CrawlConfigService configService,
                            CrawlTaskRepository taskRepository,
                            ListPageRepository listPageRepository,
                            ListItemRepository listItemRepository,
                            ArticleRepository articleRepository,
                            DetailUrlRepository detailUrlRepository) {
        this.configService = configService;
        this.taskRepository = taskRepository;
        this.listPageRepository = listPageRepository;
        this.listItemRepository = listItemRepository;
        this.articleRepository = articleRepository;
        this.detailUrlRepository = detailUrlRepository;
    }

    /**
     * 创建 RUNNING 状态任务。
     * - LIST_DETAIL:不创建 detail_url(由 CrawlEngine 从 LIST 模板抽取后建)
     * - DETAIL_ONLY:把 urls 拆成 N 条 DetailUrl(PENDING)
     */
    @Transactional
    public CrawlTask createTask(Long configId, List<String> urls) {
        CrawlConfig config = configService.getById(configId);
        CrawlTask task = new CrawlTask();
        task.setConfig(config);
        task.setPageType(config.getPageType());
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        CrawlTask saved = taskRepository.save(task);

        if (config.getPageType() == com.visualspider.enums.PageType.DETAIL_ONLY && urls != null) {
            for (String url : urls) {
                DetailUrl d = new DetailUrl();
                d.setTask(saved);
                d.setUrl(url);
                d.setStatus(DetailUrlStatus.PENDING);
                detailUrlRepository.save(d);
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public CrawlTask getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CrawlTask getByIdWithRelations(Long id) {
        return taskRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<CrawlTask> list(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<CrawlTask> listByConfigId(Long configId, Pageable pageable) {
        return taskRepository.findByConfigId(configId, pageable);
    }

    @Transactional
    public CrawlTask save(CrawlTask task) {
        return taskRepository.save(task);
    }

    /**
     * 级联删除 — 委托给 JPA cascade。
     * - listPages(ALL + orphanRemoval)→ listItems → articles
     * - detailUrls(ALL + orphanRemoval)→ articles via detail_url_id(由 service 保证)
     */
    @Transactional
    public void delete(Long id) {
        CrawlTask task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
    }
}