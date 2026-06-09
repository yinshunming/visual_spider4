package com.visualspider.dto.response;

import com.visualspider.enums.PageFetchStatus;

import java.time.Instant;

public record PageFetchResponse(
        PageFetchStatus status,
        String finalUrl,
        String title,
        long contentLength,
        Instant fetchedAt
) {
}
