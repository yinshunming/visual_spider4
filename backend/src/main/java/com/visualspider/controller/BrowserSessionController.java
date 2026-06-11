package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.response.BrowserSessionResponse;
import com.visualspider.enums.BrowserSessionStatus;
import com.visualspider.exception.BrowserSessionNotFoundException;
import com.visualspider.service.BrowserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/browser/sessions")
@RequiredArgsConstructor
public class BrowserSessionController {

    private final BrowserSessionService browserService;

    @PostMapping
    public ApiResponse<BrowserSessionResponse> open() {
        String sessionId = browserService.open();
        BrowserSessionResponse body = new BrowserSessionResponse(
                sessionId, BrowserSessionStatus.ACTIVE, browserService.getCurrentUrl(), Instant.now());
        return ApiResponse.success(body);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> close(@org.springframework.web.bind.annotation.PathVariable String id) {
        if (browserService.getSessionId() == null || !browserService.getSessionId().equals(id)) {
            throw new BrowserSessionNotFoundException(id);
        }
        browserService.close();
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<BrowserSessionResponse> status() {
        if (!browserService.isActive()) {
            return ApiResponse.success(new BrowserSessionResponse(null, BrowserSessionStatus.CLOSED, null, null));
        }
        return ApiResponse.success(new BrowserSessionResponse(
                browserService.getSessionId(), BrowserSessionStatus.ACTIVE, browserService.getCurrentUrl(), Instant.now()));
    }
}
