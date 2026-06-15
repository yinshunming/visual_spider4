package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.request.CreateConfigRequest;
import com.visualspider.dto.request.CreateFieldRequest;
import com.visualspider.dto.request.UpdateConfigRequest;
import com.visualspider.dto.response.ConfigResponse;
import com.visualspider.dto.response.FieldResponse;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.service.CrawlConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
@DisplayName("ConfigController")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CrawlConfigService configService;

    private CrawlConfig buildConfig(Long id, String name) {
        CrawlConfig config = new CrawlConfig();
        config.setId(id);
        config.setName(name);
        config.setPageType(PageType.LIST_DETAIL);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return config;
    }

    @Nested
    @DisplayName("切片 1：POST /api/v1/configs")
    class CreateEndpoint {

        @Test
        @DisplayName("合法请求返回 201 和创建后的 config")
        void create_returns201WithConfig() throws Exception {
            // Given
            CrawlConfig saved = buildConfig(1L, "新闻爬虫A");
            when(configService.create(any(CrawlConfig.class))).thenReturn(saved);

            String body = objectMapper.writeValueAsString(new CreateConfigRequest(
                    "新闻爬虫A", "https://example.com/list", PageType.LIST_DETAIL, SelectorType.CSS, ConfigStatus.STOPPED));

            // When & Then
            mockMvc.perform(post("/api/v1/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("新闻爬虫A"))
                    .andExpect(jsonPath("$.data.pageType").value("LIST_DETAIL"))
                    .andExpect(jsonPath("$.data.status").value("STOPPED"));
        }
    }

    @Nested
    @DisplayName("切片 2：GET /api/v1/configs")
    class ListEndpoint {

        @Test
        @DisplayName("返回分页 JSON 包含 configs 列表")
        void list_returnsPagedJson() throws Exception {
            // Given
            Page<CrawlConfig> page = new PageImpl<>(
                    List.of(buildConfig(1L, "配置1"), buildConfig(2L, "配置2")),
                    PageRequest.of(0, 10), 2);
            when(configService.list(any())).thenReturn(page);

            // When & Then
            mockMvc.perform(get("/api/v1/configs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].name").value("配置1"))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }
    }

    @Nested
    @DisplayName("切片 3：GET /api/v1/configs/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("存在时返回 200 和 config")
        void getById_existing_returns200() throws Exception {
            // Given
            when(configService.getById(1L)).thenReturn(buildConfig(1L, "配置1"));

            // When & Then
            mockMvc.perform(get("/api/v1/configs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("配置1"));
        }

        @Test
        @DisplayName("不存在时返回 200 + ApiResponse.error(code=404)")
        void getById_missing_returns404InEnvelope() throws Exception {
            // Given
            when(configService.getById(99L)).thenThrow(new ConfigNotFoundException(99L));

            // When & Then
            mockMvc.perform(get("/api/v1/configs/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    @DisplayName("切片 4：DELETE /api/v1/configs/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("返回 204 No Content")
        void delete_returns204() throws Exception {
            // Given
            doNothing().when(configService).deleteById(1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/configs/1"))
                    .andExpect(status().isNoContent());
            verify(configService).deleteById(1L);
        }

        @Test
        @DisplayName("不存在时返回 200 + ApiResponse.error(code=404)")
        void delete_missing_returns404InEnvelope() throws Exception {
            // Given
            doThrow(new ConfigNotFoundException(99L)).when(configService).deleteById(99L);

            // When & Then
            mockMvc.perform(delete("/api/v1/configs/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("切片 5：PUT /api/v1/configs/{id}（全量替换 fields）")
    class UpdateConfigEndpoint {

        @Test
        @DisplayName("携带 fields[] 时返回 200 + 更新后的 ConfigResponse")
        void update_withFields_returns200AndReplacesFields() throws Exception {
            // Given
            CrawlConfig updated = buildConfig(1L, "更新后名称");
            updated.setSelectorType(SelectorType.XPATH);
            updated.getFields().add(buildField(10L, "新标题"));
            when(configService.updateWithFields(eq(1L), any(), any(), any(), any(), any())).thenReturn(updated);

            String body = objectMapper.writeValueAsString(new UpdateConfigRequest(
                    "更新后名称", "https://example.com/list", PageType.LIST_DETAIL, SelectorType.XPATH,
                    List.of(new CreateFieldRequest(FieldPageType.LIST, "新标题", FieldType.TEXT, "h1.new"))));

            // When & Then
            mockMvc.perform(put("/api/v1/configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("更新后名称"))
                    .andExpect(jsonPath("$.data.selectorType").value("XPATH"))
                    .andExpect(jsonPath("$.data.fields.length()").value(1))
                    .andExpect(jsonPath("$.data.fields[0].fieldName").value("新标题"));
        }

        @Test
        @DisplayName("fields[] 携带新字段列表，service.updateWithFields 收到的字段数与请求一致")
        void update_passesAllFieldsToService() throws Exception {
            // Given
            when(configService.updateWithFields(eq(1L), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<CrawlField> fields = (List<CrawlField>) inv.getArgument(5);
                        // 模拟 service 的副作用：返回的 config 带这些 fields
                        CrawlConfig cfg = buildConfig(1L, "x");
                        cfg.getFields().addAll(fields);
                        return cfg;
                    });

            String body = objectMapper.writeValueAsString(new UpdateConfigRequest(
                    "name", "https://example.com/list", PageType.LIST_DETAIL, SelectorType.CSS,
                    List.of(
                            new CreateFieldRequest(FieldPageType.LIST, "f1", FieldType.TEXT, "h1"),
                            new CreateFieldRequest(FieldPageType.DETAIL, "f2", FieldType.URL, ".url"),
                            new CreateFieldRequest(FieldPageType.DETAIL, "f3", FieldType.DATE, ".date")
                    )));

            // When & Then
            mockMvc.perform(put("/api/v1/configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fields.length()").value(3));

            // 验证 service 收到的字段参数正确
            ArgumentCaptor<List<CrawlField>> captor = ArgumentCaptor.forClass(List.class);
            verify(configService).updateWithFields(eq(1L), any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue()).hasSize(3);
            assertThat(captor.getValue()).extracting(CrawlField::getFieldName)
                    .containsExactly("f1", "f2", "f3");
        }

        @Test
        @DisplayName("fields[] 为空数组表示清空所有字段（service 收到空列表）")
        void update_emptyFieldsArray_clearsAllFields() throws Exception {
            // Given
            when(configService.updateWithFields(eq(1L), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        CrawlConfig cfg = buildConfig(1L, "x");
                        return cfg;
                    });

            String body = objectMapper.writeValueAsString(new UpdateConfigRequest(
                    "name", "https://example.com/list", PageType.LIST_DETAIL, SelectorType.CSS, List.of()));

            // When & Then
            mockMvc.perform(put("/api/v1/configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fields.length()").value(0));

            ArgumentCaptor<List<CrawlField>> captor = ArgumentCaptor.forClass(List.class);
            verify(configService).updateWithFields(eq(1L), any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("不存在的 id 返回 200 + ApiResponse.error(code=404)")
        void update_notFound_returns404InEnvelope() throws Exception {
            // Given
            when(configService.updateWithFields(eq(99L), any(), any(), any(), any(), any()))
                    .thenThrow(new ConfigNotFoundException(99L));

            String body = objectMapper.writeValueAsString(new UpdateConfigRequest(
                    "x", "https://example.com/list", PageType.LIST_DETAIL, SelectorType.CSS, List.of()));

            // When & Then
            mockMvc.perform(put("/api/v1/configs/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    private CrawlField buildField(Long id, String name) {
        CrawlField field = new CrawlField();
        field.setId(id);
        field.setPageType(FieldPageType.LIST);
        field.setFieldName(name);
        field.setFieldType(FieldType.TEXT);
        field.setSelector("h1");
        return field;
    }
}
