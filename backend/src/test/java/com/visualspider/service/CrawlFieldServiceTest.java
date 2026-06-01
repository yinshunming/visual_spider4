package com.visualspider.service;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.repository.CrawlConfigRepository;
import com.visualspider.repository.CrawlFieldRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlFieldService")
class CrawlFieldServiceTest {

    @Mock
    private CrawlFieldRepository fieldRepository;

    @Mock
    private CrawlConfigRepository configRepository;

    @InjectMocks
    private CrawlFieldService service;

    private CrawlConfig buildConfig(Long id) {
        CrawlConfig config = new CrawlConfig();
        config.setId(id);
        config.setName("测试配置");
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return config;
    }

    private CrawlField buildField(CrawlConfig config, String name) {
        CrawlField field = new CrawlField();
        field.setConfig(config);
        field.setPageType(FieldPageType.LIST);
        field.setFieldName(name);
        field.setFieldType(FieldType.TEXT);
        field.setSelector(".selector");
        return field;
    }

    @Nested
    @DisplayName("切片 1：为存在的配置添加字段")
    class AddField {

        @Test
        @DisplayName("config 存在时创建字段并关联 config")
        void addField_configExists_savesField() {
            // Given
            CrawlConfig config = buildConfig(1L);
            when(configRepository.findById(1L)).thenReturn(Optional.of(config));
            when(fieldRepository.save(any(CrawlField.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            CrawlField result = service.addField(1L, buildField(config, "标题"));

            // Then
            ArgumentCaptor<CrawlField> captor = ArgumentCaptor.forClass(CrawlField.class);
            verify(fieldRepository).save(captor.capture());
            assertThat(captor.getValue().getConfig()).isEqualTo(config);
            assertThat(result.getFieldName()).isEqualTo("标题");
        }
    }

    @Nested
    @DisplayName("切片 2：为不存在的配置添加字段抛 ConfigNotFoundException")
    class AddFieldConfigMissing {

        @Test
        @DisplayName("config 不存在时抛 ConfigNotFoundException，不调用 fieldRepository.save")
        void addField_configMissing_throws() {
            // Given
            CrawlConfig orphan = buildConfig(99L);
            when(configRepository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.addField(99L, buildField(orphan, "x")))
                    .isInstanceOf(ConfigNotFoundException.class)
                    .hasMessageContaining("99");
            verify(fieldRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("切片 3：按 id 更新字段")
    class UpdateField {

        @Test
        @DisplayName("字段存在时更新并返回新值")
        void update_fieldExists_updatesAndReturns() {
            // Given
            CrawlConfig config = buildConfig(1L);
            CrawlField existing = buildField(config, "原名称");
            existing.setId(10L);
            when(fieldRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(fieldRepository.save(any(CrawlField.class))).thenAnswer(inv -> inv.getArgument(0));

            CrawlField updates = buildField(config, "新名称");
            updates.setFieldType(FieldType.NUMBER);
            updates.setSelector(".new-selector");
            updates.setPageType(FieldPageType.DETAIL);

            // When
            CrawlField result = service.update(10L, updates);

            // Then
            assertThat(result.getFieldName()).isEqualTo("新名称");
            assertThat(result.getFieldType()).isEqualTo(FieldType.NUMBER);
            assertThat(result.getSelector()).isEqualTo(".new-selector");
            assertThat(result.getPageType()).isEqualTo(FieldPageType.DETAIL);
            assertThat(result.getId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("切片 4：按 id 删除字段")
    class DeleteField {

        @Test
        @DisplayName("字段存在时调用 repository.deleteById")
        void deleteById_existing_callsRepository() {
            // Given
            when(fieldRepository.existsById(20L)).thenReturn(true);

            // When
            service.deleteById(20L);

            // Then
            verify(fieldRepository).deleteById(20L);
        }

        @Test
        @DisplayName("字段不存在时抛异常，不调用 delete")
        void deleteById_missing_throws() {
            // Given
            when(fieldRepository.existsById(99L)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> service.deleteById(99L))
                    .isInstanceOf(ConfigNotFoundException.class);
            verify(fieldRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("切片 5：按 configId 查询字段列表")
    class ListByConfigId {

        @Test
        @DisplayName("返回 Repository 的查询结果")
        void listByConfigId_returnsRepositoryResult() {
            // Given
            CrawlConfig config = buildConfig(1L);
            when(configRepository.existsById(1L)).thenReturn(true);
            CrawlField f1 = buildField(config, "A");
            CrawlField f2 = buildField(config, "B");
            when(fieldRepository.findByConfigIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(f1, f2));

            // When
            List<CrawlField> result = service.listByConfigId(1L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(CrawlField::getFieldName).containsExactly("A", "B");
        }

        @Test
        @DisplayName("config 不存在时抛 ConfigNotFoundException")
        void listByConfigId_configMissing_throws() {
            // Given
            when(configRepository.existsById(99L)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> service.listByConfigId(99L))
                    .isInstanceOf(ConfigNotFoundException.class);
        }
    }
}
