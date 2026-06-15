package com.visualspider.repository;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository 集成测试：使用本机手工启动的 PostgreSQL 服务（库 visual_spider4_test），
 * 通过 application-test.yml 配置连接。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("CrawlFieldRepository")
class CrawlFieldRepositoryTest {

    @Autowired
    private CrawlConfigRepository configRepository;

    @Autowired
    private CrawlFieldRepository fieldRepository;

    private CrawlConfig createConfig() {
        CrawlConfig config = new CrawlConfig();
        config.setName("测试配置");
        config.setStartUrl("https://example.com/test");
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return configRepository.save(config);
    }

    private CrawlField buildField(CrawlConfig config, String name) {
        CrawlField field = new CrawlField();
        field.setConfig(config);
        field.setPageType(FieldPageType.LIST);
        field.setFieldName(name);
        field.setFieldType(FieldType.TEXT);
        field.setSelector(".selector");
        // 维护双向关系，确保 JPA cascade 能正常工作
        config.getFields().add(field);
        return field;
    }

    @Nested
    @DisplayName("切片 1：按 configId 查询并按 createdAt ASC 排序")
    class FindByConfigId {

        @Test
        @DisplayName("为指定 configId 保存字段后可通过 configId 查询到，并按创建时间升序")
        void findByConfigId_returnsFieldsOrderedByCreatedAt() {
            // Given
            CrawlConfig config = createConfig();
            fieldRepository.save(buildField(config, "字段A"));
            fieldRepository.save(buildField(config, "字段B"));
            fieldRepository.save(buildField(config, "字段C"));

            // When
            List<CrawlField> fields = fieldRepository.findByConfigIdOrderByCreatedAtAsc(config.getId());

            // Then
            assertThat(fields).hasSize(3);
            assertThat(fields)
                    .extracting(CrawlField::getFieldName)
                    .containsExactly("字段A", "字段B", "字段C");
        }

        @Test
        @DisplayName("无字段时返回空列表")
        void findByConfigId_whenNoFields_returnsEmptyList() {
            // Given
            CrawlConfig config = createConfig();

            // When
            List<CrawlField> fields = fieldRepository.findByConfigIdOrderByCreatedAtAsc(config.getId());

            // Then
            assertThat(fields).isEmpty();
        }
    }

    @Nested
    @DisplayName("切片 2：删除配置时级联删除关联字段")
    class CascadeDelete {

        @Test
        @DisplayName("删除配置后，关联字段也被删除")
        void deleteConfig_cascadesToFields() {
            // Given
            CrawlConfig config = createConfig();
            fieldRepository.save(buildField(config, "字段1"));
            fieldRepository.save(buildField(config, "字段2"));
            assertThat(fieldRepository.findByConfigIdOrderByCreatedAtAsc(config.getId())).hasSize(2);

            // When - 重新加载以触发 JPA 级联
            CrawlConfig loaded = configRepository.findById(config.getId()).orElseThrow();
            configRepository.delete(loaded);

            // Then
            assertThat(fieldRepository.findByConfigIdOrderByCreatedAtAsc(config.getId())).isEmpty();
        }
    }
}
