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
     * - listPages(ALL + orphanRemoval)→ listItems
     * - detailUrls(ALL + orphanRemoval)
     * - articles:单向 ManyToOne/OneToOne 无 cascade,必须显式先删,否则
     *   list_item_id / detail_url_id 外键约束违反。
     */
    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        // Article 不在 JPA 级联链上,必须显式先批量删,避免 list_item_id /
        // detail_url_id / task_id 外键约束违反。@Modifying clearAutomatically=true
        // 会清空 L1,所以这里用 deleteById 内部重新加载 task 为 managed,
        // 确保 em.remove 触发 listPages/detailUrls 的级联删除(orphanRemoval)。
        articleRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);
    }
}