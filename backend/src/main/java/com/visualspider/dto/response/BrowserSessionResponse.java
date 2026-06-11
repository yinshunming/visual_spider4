package com.visualspider.dto.response;

import com.visualspider.enums.BrowserSessionStatus;

import java.time.Instant;

public record BrowserSessionResponse(
        String sessionId,
        BrowserSessionStatus status,
        String currentUrl,
        Instant createdAt) {
}
