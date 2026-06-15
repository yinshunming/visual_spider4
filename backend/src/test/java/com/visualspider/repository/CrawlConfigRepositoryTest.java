package com.visualspider.repository;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository 集成测试：使用本机手工启动的 PostgreSQL 服务（库 visual_spider4_test），
 * 通过 application-test.yml 配置连接。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("CrawlConfigRepository")
class CrawlConfigRepositoryTest {

    @Autowired
    private CrawlConfigRepository repository;

    private CrawlConfig buildConfig(String name) {
        CrawlConfig config = new CrawlConfig();
        config.setName(name);
        config.setStartUrl("https://example.com/" + name);
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return config;
    }

    @Nested
    @DisplayName("切片 1：保存并通过 id 查询")
    class SaveAndFind {

        @Test
        @DisplayName("保存后可通过 id 查询到，字段保持一致")
        void save_thenFindById_returnsSavedConfig() {
            // Given
            CrawlConfig config = buildConfig("新闻爬虫A");

            // When
            CrawlConfig saved = repository.save(config);
            Optional<CrawlConfig> found = repository.findById(saved.getId());

            // Then
            assertThat(found).isPresent();
            CrawlConfig actual = found.get();
            assertThat(actual.getId()).isNotNull();
            assertThat(actual.getName()).isEqualTo("新闻爬虫A");
            assertThat(actual.getPageType()).isEqualTo(PageType.LIST_DETAIL);
            assertThat(actual.getSelectorType()).isEqualTo(SelectorType.CSS);
            assertThat(actual.getStatus()).isEqualTo(ConfigStatus.STOPPED);
            assertThat(actual.getCreatedAt()).isNotNull();
            assertThat(actual.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("切片 2：分页查询（按 createdAt DESC 排序）")
    class PagedList {

        @Test
        @DisplayName("多条数据时分页返回按 createdAt DESC 排序的结果")
        void findAll_withPageable_returnsPagedAndSortedByCreatedAtDesc() {
            // Given
            repository.save(buildConfig("配置A"));
            repository.save(buildConfig("配置B"));
            repository.save(buildConfig("配置C"));
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

            // When
            Page<CrawlConfig> page = repository.findAll(pageable);

            // Then
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent())
                    .extracting(CrawlConfig::getName)
                    .containsExactly("配置C", "配置B", "配置A");
        }

        @Test
        @DisplayName("无数据时返回空分页")
        void findAll_whenEmpty_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<CrawlConfig> page = repository.findAll(pageable);

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("切片 3：按 id 删除")
    class DeleteById {

        @Test
        @DisplayName("删除后再次查询返回空")
        void deleteById_thenFindById_returnsEmpty() {
            // Given
            CrawlConfig saved = repository.save(buildConfig("待删除配置"));
            Long id = saved.getId();
            assertThat(repository.findById(id)).isPresent();

            // When
            repository.deleteById(id);

            // Then
            assertThat(repository.findById(id)).isEmpty();
        }
    }
}
