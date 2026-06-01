package com.visualspider.controller;

import com.visualspider.dto.request.CreateFieldRequest;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.service.CrawlFieldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FieldController.class)
@DisplayName("FieldController")
class FieldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CrawlFieldService fieldService;

    private CrawlField buildField(Long id, String name) {
        CrawlField field = new CrawlField();
        field.setId(id);
        field.setPageType(FieldPageType.LIST);
        field.setFieldName(name);
        field.setFieldType(FieldType.TEXT);
        field.setSelector(".selector");
        return field;
    }

    @Nested
    @DisplayName("切片 1：POST /api/v1/configs/{configId}/fields")
    class AddField {

        @Test
        @DisplayName("返回 201 和创建的字段")
        void addField_returns201WithField() throws Exception {
            // Given
            when(fieldService.addField(eq(1L), any(CrawlField.class))).thenReturn(buildField(10L, "标题"));
            String body = objectMapper.writeValueAsString(
                    new CreateFieldRequest(FieldPageType.LIST, "标题", FieldType.TEXT, ".title"));

            // When & Then
            mockMvc.perform(post("/api/v1/configs/1/fields")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.fieldName").value("标题"));
        }
    }

    @Nested
    @DisplayName("切片 2：GET /api/v1/configs/{configId}/fields")
    class ListFields {

        @Test
        @DisplayName("返回字段列表")
        void listFields_returnsList() throws Exception {
            // Given
            when(fieldService.listByConfigId(1L))
                    .thenReturn(List.of(buildField(10L, "A"), buildField(11L, "B")));

            // When & Then
            mockMvc.perform(get("/api/v1/configs/1/fields"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].fieldName").value("A"));
        }
    }

    @Nested
    @DisplayName("切片 3：PUT /api/v1/fields/{id}")
    class UpdateField {

        @Test
        @DisplayName("返回 200 和更新后的字段")
        void update_returns200() throws Exception {
            // Given
            when(fieldService.update(eq(10L), any(CrawlField.class))).thenReturn(buildField(10L, "新标题"));
            String body = objectMapper.writeValueAsString(
                    new CreateFieldRequest(FieldPageType.DETAIL, "新标题", FieldType.NUMBER, ".new"));

            // When & Then
            mockMvc.perform(put("/api/v1/fields/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.fieldName").value("新标题"));
        }
    }

    @Nested
    @DisplayName("切片 4：DELETE /api/v1/fields/{id}")
    class DeleteField {

        @Test
        @DisplayName("返回 204 No Content")
        void delete_returns204() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/fields/10"))
                    .andExpect(status().isNoContent());
            verify(fieldService).deleteById(10L);
        }

        @Test
        @DisplayName("字段不存在时返回 200 + ApiResponse.error(code=404)")
        void delete_missing_returns404InEnvelope() throws Exception {
            // Given
            doThrow(new ConfigNotFoundException(99L)).when(fieldService).deleteById(99L);

            // When & Then
            mockMvc.perform(delete("/api/v1/fields/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
