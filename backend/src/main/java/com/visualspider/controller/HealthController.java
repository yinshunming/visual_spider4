package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.HealthResponse;
import com.visualspider.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        HealthResponse response = healthService.checkHealth();
        return ApiResponse.success(response);
    }
}
