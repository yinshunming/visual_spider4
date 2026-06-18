package com.visualspider.repository;

import com.visualspider.entity.Article;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlTask;
import com.visualspider.entity.ListItem;
import com.visualspider.entity.ListPage;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.ItemStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.enums.TaskStatus;
import com.visualspider.service.ArticleQueryService;
import com.visualspider.service.CrawlTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleQueryService 导出回归测试:本机 PostgreSQL(库 visual_spider4_test)。
 * 复现 exportJson 经 native query findByConfigIdAndKeyword + Sort 时,
 * 属性名 fetchedAt 被 PG 折叠成 fetchedat 导致 "column does not exist" 的 bug。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ArticleQueryService.class, CrawlTaskService.class, com.visualspider.service.CrawlConfigService.class})
@DisplayName("ArticleQueryService 导出")
class ArticleRepositoryTest {

    @Autowired
    private ArticleQueryService queryService;
    @Autowired
    private CrawlTaskService taskService;
    @Autowired
    private CrawlConfigRepository configRepository;
    @Autowired
    private CrawlTaskRepository taskRepository;
    @Autowired
    private ListPageRepository listPageRepository;
    @Autowired
    private ListItemRepository listItemRepository;
    @Autowired
    private ArticleRepository articleRepository;

    private Article saveArticle(CrawlConfig config, CrawlTask task, ListItem item, String title) {
        Article a = new Article();
        a.setTask(task);
        a.setConfig(config);
        a.setListItem(item);
        a.setUrl("https://example.com/" + title);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", title);
        a.setCustomFields(fields);
        a.setStatus(ItemStatus.CRAWLED);
        return articleRepository.save(a);
    }

    /** 建一套 LIST_DETAIL 任务图:config → task → listPage → listItem → article。 */
    private CrawlTask seedTaskWithArticle(String title) {
        CrawlConfig config = new CrawlConfig();
        config.setName("cfg");
        config.setStartUrl("https://example.com/list");
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        config = configRepository.save(config);

        CrawlTask task = new CrawlTask();
        task.setConfig(config);
        task.setPageType(PageType.LIST_DETAIL);
        task.setStatus(TaskStatus.COMPLETED);
        task.setTotalItems(1);
        task.setCrawledItems(1);
        task.setFailedItems(0);
        task.setStartedAt(java.time.Instant.now());
        task = taskRepository.save(task);

        ListPage listPage = new ListPage();
        listPage.setTask(task);
        listPage.setConfig(config);
        listPage.setUrl("https://example.com/list");
        listPage = listPageRepository.save(listPage);

        ListItem item = new ListItem();
        item.setListPage(listPage);
        item.setDetailUrl("https://example.com/a");
        item.setStatus(ItemStatus.CRAWLED);
        item = listItemRepository.save(item);

        saveArticle(config, task, item, title);
        return task;
    }

    @Nested
    @DisplayName("exportJson")
    class ExportJson {

        @Test
        @DisplayName("带 article 时导出不报列名错误,返回含 custom_fields 的行")
        void exportJson_withArticles_returnsRowsWithoutColumnError() {
            CrawlTask task = seedTaskWithArticle("字母哥");

            // bug:aggregateCustomFieldKeys 用 Sort.by("fetchedAt") 传给 nativeQuery,
            // PG 折叠成 fetchedat → "column a.fetchedat does not exist",exportJson 抛异常。
            List<Map<String, String>> rows = queryService.exportJson(task.getConfig().getId(), null);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("title")).isEqualTo("字母哥");
            assertThat(rows.get(0).get("url")).isEqualTo("https://example.com/字母哥");
        }
    }

    @Nested
    @DisplayName("delete 任务级联")
    class DeleteTaskCascade {

        @Test
        @DisplayName("删带 article 的任务不触发外键约束违反,task 与 article 一并清除")
        void delete_taskWithArticle_cascadesWithoutFkViolation() {
            CrawlTask task = seedTaskWithArticle("字母哥");
            Long taskId = task.getId();
            assertThat(taskRepository.findById(taskId)).isPresent();
            assertThat(articleRepository.findByTaskId(taskId, org.springframework.data.domain.PageRequest.of(0, 10)))
                    .hasSize(1);

            // bug:service.delete 未先删 article,task 级联删 list_item 时被
            // article.list_item_id 外键引用 → "violates foreign key constraint"。
            taskService.delete(taskId);

            assertThat(taskRepository.findById(taskId)).isEmpty();
            assertThat(articleRepository.findByTaskId(taskId, org.springframework.data.domain.PageRequest.of(0, 10)))
                    .isEmpty();
        }
    }
}
