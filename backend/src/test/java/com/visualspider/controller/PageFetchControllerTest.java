package com.visualspider.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualspider.dto.request.PageFetchRequest;
import com.visualspider.dto.response.PageFetchResponse;
import com.visualspider.enums.PageFetchStatus;
import com.visualspider.exception.BlockedAddressException;
import com.visualspider.exception.FetchFailedException;
import com.visualspider.exception.FetchTimeoutException;
import com.visualspider.service.PageFetchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageFetchController.class)
@DisplayName("PageFetchController")
class PageFetchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PageFetchService pageFetchService;

    @Nested
    @DisplayName("§12 成功路径")
    class SuccessPath {

        @Test
        @DisplayName("POST /api/v1/page-fetch 返回 200 + status=SUCCESS")
        void postReturns200() throws Exception {
            PageFetchResponse svcResp = new PageFetchResponse(
                    PageFetchStatus.SUCCESS,
                    "https://example.com",
                    "Example Domain",
                    1234L,
                    Instant.parse("2026-06-01T00:00:00Z")
            );
            when(pageFetchService.fetch(any(URI.class))).thenReturn(svcResp);

            String body = objectMapper.writeValueAsString(new PageFetchRequest("https://example.com"));

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.title").value("Example Domain"))
                    .andExpect(jsonPath("$.data.finalUrl").value("https://example.com"))
                    .andExpect(jsonPath("$.data.contentLength").value(1234));
        }
    }

    @Nested
    @DisplayName("§13 URL 校验失败 → 400/4001")
    class InvalidUrl {

        @Test
        @DisplayName("空 url 返回 400 + code=4001")
        void emptyUrlReturns400() throws Exception {
            String body = "{\"url\":\"\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(4001));
        }

        @Test
        @DisplayName("非法 URI 字符串返回 400 + code=4001")
        void malformedUrlReturns400() throws Exception {
            String body = "{\"url\":\"not-a-url\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(4001));
        }

        @Test
        @DisplayName("file:// 协议返回 400 + code=4001")
        void fileSchemeReturns400() throws Exception {
            String body = "{\"url\":\"file:///etc/passwd\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(4001));
        }
    }

    @Nested
    @DisplayName("§14 异常映射")
    class ExceptionMapping {

        @Test
        @DisplayName("BlockedAddressException → 403 + code=4003")
        void blockedAddressReturns403() throws Exception {
            when(pageFetchService.fetch(any(URI.class)))
                    .thenThrow(new BlockedAddressException("目标地址被禁止访问"));

            String body = "{\"url\":\"http://127.0.0.1\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(4003))
                    .andExpect(jsonPath("$.message").value("目标地址被禁止访问"));
        }

        @Test
        @DisplayName("FetchTimeoutException → 504 + code=4004")
        void timeoutReturns504() throws Exception {
            when(pageFetchService.fetch(any(URI.class)))
                    .thenThrow(new FetchTimeoutException("页面加载超时（8s）"));

            String body = "{\"url\":\"https://example.com\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.code").value(4004));
        }

        @Test
        @DisplayName("FetchFailedException unreachable → 502 + code=4002")
        void unreachableReturns502() throws Exception {
            when(pageFetchService.fetch(any(URI.class)))
                    .thenThrow(FetchFailedException.unreachable("Connection refused"));

            String body = "{\"url\":\"https://example.com\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value(4002));
        }

        @Test
        @DisplayName("FetchFailedException tooLarge → 502 + code=4005")
        void tooLargeReturns502() throws Exception {
            when(pageFetchService.fetch(any(URI.class)))
                    .thenThrow(FetchFailedException.tooLarge());

            String body = "{\"url\":\"https://example.com\"}";

            mockMvc.perform(post("/api/v1/page-fetch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value(4005));
        }
    }
}
