package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.HealthResponse;
import com.visualspider.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    void getHealth_returns200WithStatusUp() throws Exception {
        HealthResponse healthResponse = new HealthResponse("UP", "UP", Instant.now().toString(), null);
        when(healthService.checkHealth()).thenReturn(healthResponse);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").exists())
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void getHealth_containsRequiredFields() throws Exception {
        HealthResponse healthResponse = new HealthResponse("UP", "UP", "2024-01-01T00:00:00Z", null);
        when(healthService.checkHealth()).thenReturn(healthResponse);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.database").exists())
                .andExpect(jsonPath("$.data.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getHealth_whenDatabaseDown_returnsDatabaseDown() throws Exception {
        HealthResponse healthResponse = new HealthResponse("UP", "DOWN", Instant.now().toString(), "PG_NOT_READY: PostgreSQL 未启动");
        when(healthService.checkHealth()).thenReturn(healthResponse);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("DOWN"))
                .andExpect(jsonPath("$.data.message").value("PG_NOT_READY: PostgreSQL 未启动"));
    }
}
