package com.visualspider.controller;

import com.visualspider.exception.BrowserSessionAlreadyActiveException;
import com.visualspider.service.BrowserSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowserSessionController.class)
@DisplayName("BrowserSessionController")
class BrowserSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrowserSessionService browserService;

    @Nested
    @DisplayName("POST /api/v1/browser/sessions")
    class Open {

        @Test
        @DisplayName("open 返回 200 + sessionId")
        void openReturnsSessionId() throws Exception {
            when(browserService.open()).thenReturn("test-session-id");

            mockMvc.perform(post("/api/v1/browser/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.sessionId").value("test-session-id"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("重复 open 抛 BrowserSessionAlreadyActiveException → HTTP 409")
        void openTwiceReturns409() throws Exception {
            when(browserService.open()).thenThrow(new BrowserSessionAlreadyActiveException());

            mockMvc.perform(post("/api/v1/browser/sessions"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/browser/sessions/{id}")
    class Close {

        @Test
        @DisplayName("close 返回 200")
        void closeReturns200() throws Exception {
            when(browserService.getSessionId()).thenReturn("test-session-id");

            mockMvc.perform(delete("/api/v1/browser/sessions/test-session-id"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/browser/sessions")
    class Status {

        @Test
        @DisplayName("无活跃时 status=CLOSED")
        void noActiveSession() throws Exception {
            when(browserService.isActive()).thenReturn(false);

            mockMvc.perform(get("/api/v1/browser/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));
        }

        @Test
        @DisplayName("有活跃时返回 sessionId + currentUrl")
        void activeSession() throws Exception {
            when(browserService.isActive()).thenReturn(true);
            when(browserService.getSessionId()).thenReturn("test-session-id");
            when(browserService.getCurrentUrl()).thenReturn("https://example.com");

            mockMvc.perform(get("/api/v1/browser/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.sessionId").value("test-session-id"))
                    .andExpect(jsonPath("$.data.currentUrl").value("https://example.com"));
        }
    }
}
