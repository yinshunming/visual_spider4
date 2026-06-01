package com.visualspider.service;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.repository.CrawlConfigRepository;
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
@DisplayName("CrawlConfigService")
class CrawlConfigServiceTest {

    @Mock
    private CrawlConfigRepository repository;

    @InjectMocks
    private CrawlConfigService service;

    private CrawlConfig buildConfig() {
        CrawlConfig config = new CrawlConfig();
        config.setName("测试配置");
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        return config;
    }

    @Nested
    @DisplayName("切片 1：create() - status 默认 STOPPED")
    class Create {

        @Test
        @DisplayName("创建配置时 status 自动设为 STOPPED")
        void create_setsDefaultStatusToStopped() {
            // Given
            CrawlConfig input = buildConfig();
            when(repository.save(any(CrawlConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            CrawlConfig result = service.create(input);

            // Then
            ArgumentCaptor<CrawlConfig> captor = ArgumentCaptor.forClass(CrawlConfig.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ConfigStatus.STOPPED);
            assertThat(result.getStatus()).isEqualTo(ConfigStatus.STOPPED);
        }

        @Test
        @DisplayName("调用方显式设置 status 时也允许（被覆盖）")
        void create_preservesExplicitStatus() {
            // Given
            CrawlConfig input = buildConfig();
            input.setStatus(ConfigStatus.ACTIVE);
            when(repository.save(any(CrawlConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            CrawlConfig result = service.create(input);

            // Then
            assertThat(result.getStatus()).isEqualTo(ConfigStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("切片 2：list(Pageable) - 分页查询")
    class PagedList {

        @Test
        @DisplayName("分页返回 Repository 的结果")
        void list_returnsPageFromRepository() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            CrawlConfig c1 = buildConfig();
            c1.setName("配置1");
            Page<CrawlConfig> expected = new PageImpl<>(List.of(c1), pageable, 1);
            when(repository.findAll(pageable)).thenReturn(expected);

            // When
            Page<CrawlConfig> result = service.list(pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("配置1");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("切片 3：getById() - 不存在时抛 ConfigNotFoundException")
    class GetById {

        @Test
        @DisplayName("存在时返回配置")
        void getById_existing_returnsConfig() {
            // Given
            CrawlConfig config = buildConfig();
            config.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(config));

            // When
            CrawlConfig result = service.getById(1L);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("测试配置");
        }

        @Test
        @DisplayName("不存在时抛出 ConfigNotFoundException")
        void getById_missing_throwsConfigNotFoundException() {
            // Given
            when(repository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(ConfigNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("切片 4：deleteById() - 委托给 Repository")
    class DeleteById {

        @Test
        @DisplayName("存在时调用 repository.delete")
        void deleteById_existing_callsRepositoryDelete() {
            // Given
            CrawlConfig config = buildConfig();
            config.setId(5L);
            when(repository.findById(5L)).thenReturn(Optional.of(config));

            // When
            service.deleteById(5L);

            // Then
            verify(repository).delete(config);
        }

        @Test
        @DisplayName("不存在时抛 ConfigNotFoundException，不调用 delete")
        void deleteById_missing_throwsAndDoesNotDelete() {
            // Given
            when(repository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.deleteById(99L))
                    .isInstanceOf(ConfigNotFoundException.class);
            verify(repository, never()).delete(any());
        }
    }
}
