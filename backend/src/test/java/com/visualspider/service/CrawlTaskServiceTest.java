package com.visualspider.service;

import com.visualspider.entity.Article;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlTask;
import com.visualspider.entity.DetailUrl;
import com.visualspider.entity.ListItem;
import com.visualspider.entity.ListPage;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.DetailUrlStatus;
import com.visualspider.enums.ItemStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.enums.TaskStatus;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.exception.TaskNotFoundException;
import com.visualspider.repository.ArticleRepository;
import com.visualspider.repository.CrawlTaskRepository;
import com.visualspider.repository.DetailUrlRepository;
import com.visualspider.repository.ListItemRepository;
import com.visualspider.repository.ListPageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlTaskService")
class CrawlTaskServiceTest {

    @Mock
    private CrawlConfigService configService;
    @Mock
    private CrawlTaskRepository taskRepository;
    @Mock
    private ListPageRepository listPageRepository;
    @Mock
    private ListItemRepository listItemRepository;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private DetailUrlRepository detailUrlRepository;

    @InjectMocks
    private CrawlTaskService service;

    private CrawlConfig buildConfig(PageType type) {
        CrawlConfig config = new CrawlConfig();
        config.setId(1L);
        config.setName("cfg");
        config.setStartUrl("https://example.com/list");
        config.setPageType(type);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return config;
    }

    @Nested
    @DisplayName("createTask")
    class CreateTask {

        @Test
        @DisplayName("LIST_DETAIL 创建 RUNNING 状态 task,不创建 detail_url")
        void listDetail_createsRunningTask_withoutDetailUrls() {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            when(configService.getById(1L)).thenReturn(config);
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));

            CrawlTask task = service.createTask(1L, null);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
            assertThat(task.getConfig()).isSameAs(config);
            assertThat(task.getPageType()).isEqualTo(PageType.LIST_DETAIL);
            assertThat(task.getStartedAt()).isNotNull();
            assertThat(task.getCompletedAt()).isNull();
            verify(detailUrlRepository, never()).save(any());
        }

        @Test
        @DisplayName("DETAIL_ONLY 创建 RUNNING 状态 task,把 urls 拆成 N 条 DetailUrl")
        void detailOnly_createsRunningTask_withPendingDetailUrls() {
            CrawlConfig config = buildConfig(PageType.DETAIL_ONLY);
            when(configService.getById(1L)).thenReturn(config);
            when(taskRepository.save(any(CrawlTask.class))).thenAnswer(inv -> inv.getArgument(0));
            when(detailUrlRepository.save(any(DetailUrl.class))).thenAnswer(inv -> inv.getArgument(0));

            CrawlTask task = service.createTask(1L,
                    List.of("https://example.com/a", "https://example.com/b", "https://example.com/c"));

            assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
            ArgumentCaptor<DetailUrl> captor = ArgumentCaptor.forClass(DetailUrl.class);
            verify(detailUrlRepository, org.mockito.Mockito.times(3)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(DetailUrl::getUrl)
                    .containsExactly("https://example.com/a", "https://example.com/b", "https://example.com/c");
            assertThat(captor.getAllValues()).allMatch(d -> d.getStatus() == DetailUrlStatus.PENDING);
            assertThat(captor.getAllValues()).allMatch(d -> d.getTask() == task);
        }

        @Test
        @DisplayName("config 不存在抛 ConfigNotFoundException")
        void missingConfig_throws() {
            when(configService.getById(99L)).thenThrow(new ConfigNotFoundException(99L));

            assertThatThrownBy(() -> service.createTask(99L, null))
                    .isInstanceOf(ConfigNotFoundException.class);
            verify(taskRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById / getByIdWithRelations / delete")
    class ReadAndDelete {

        @Test
        @DisplayName("getById 不存在抛 TaskNotFoundException")
        void getById_missing_throws() {
            when(taskRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("getByIdWithRelations 不存在抛 TaskNotFoundException")
        void getByIdWithRelations_missing_throws() {
            when(taskRepository.findByIdWithRelations(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getByIdWithRelations(99L)).isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("delete 不存在抛 TaskNotFoundException,不调 deleteByTaskId / deleteById")
        void delete_missing_throwsAndDoesNotDelete() {
            when(taskRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(TaskNotFoundException.class);
            verify(articleRepository, never()).deleteByTaskId(any());
            verify(taskRepository, never()).deleteById(any());
            verify(taskRepository, never()).delete(any());
        }

        @Test
        @DisplayName("delete 存在时先 bulk 删 articles 再 deleteById task,避免 article 外键约束违反")
        void delete_existing_deletesArticlesBeforeTask() {
            when(taskRepository.existsById(1L)).thenReturn(true);

            service.delete(1L);

            // 顺序:existsById → deleteByTaskId → deleteById
            // deleteById 内部重新加载 task 为 managed,确保级联能正确处理 listPages/detailUrls
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(taskRepository, articleRepository);
            inOrder.verify(taskRepository).existsById(1L);
            inOrder.verify(articleRepository).deleteByTaskId(1L);
            inOrder.verify(taskRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("listTasks")
    class ListTasks {

        @Test
        @DisplayName("分页返回 Repository 结果")
        void list_returnsRepositoryPage() {
            Pageable pageable = PageRequest.of(0, 10);
            CrawlTask t = new CrawlTask();
            t.setId(1L);
            Page<CrawlTask> page = new PageImpl<>(List.of(t), pageable, 1);
            when(taskRepository.findAll(pageable)).thenReturn(page);

            Page<CrawlTask> result = service.list(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("save 直接委托给 repository.save")
        void save_delegates() {
            CrawlTask t = new CrawlTask();
            when(taskRepository.save(t)).thenReturn(t);
            assertThat(service.save(t)).isSameAs(t);
        }
    }

    @Nested
    @DisplayName("实体冒烟")
    class EntitySmoke {

        @Test
        @DisplayName("CrawlTask 默认字段值合理")
        void crawlTask_defaults() {
            CrawlTask t = new CrawlTask();
            assertThat(t.getTotalItems()).isZero();
            assertThat(t.getCrawledItems()).isZero();
            assertThat(t.getFailedItems()).isZero();
            assertThat(t.getListPages()).isEmpty();
            assertThat(t.getDetailUrls()).isEmpty();
        }

        @Test
        @DisplayName("ListPage 默认字段值合理")
        void listPage_defaults() {
            ListPage p = new ListPage();
            assertThat(p.getListItems()).isEmpty();
        }

        @Test
        @DisplayName("ListItem 默认字段值合理")
        void listItem_defaults() {
            ListItem i = new ListItem();
            assertThat(i.getStatus()).isNull();
            assertThat(i.getCustomFields()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Article 默认字段值合理")
        void article_defaults() {
            Article a = new Article();
            assertThat(a.getStatus()).isNull();
            assertThat(a.getCustomFields()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("DetailUrl 默认字段值合理")
        void detailUrl_defaults() {
            DetailUrl d = new DetailUrl();
            assertThat(d.getStatus()).isNull();
        }

        @Test
        @DisplayName("ItemStatus 枚举值")
        void itemStatus_values() {
            assertThat(ItemStatus.values()).containsExactly(ItemStatus.PENDING, ItemStatus.CRAWLED, ItemStatus.FAILED);
        }
    }
}