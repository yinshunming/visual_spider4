package com.visualspider.dto.response;

import com.visualspider.entity.CrawlTask;
import com.visualspider.enums.PageType;
import com.visualspider.enums.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        Long id,
        Long configId,
        PageType pageType,
        TaskStatus status,
        int totalItems,
        int crawledItems,
        int failedItems,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public static TaskResponse from(CrawlTask t) {
        return new TaskResponse(
                t.getId(),
                t.getConfig() == null ? null : t.getConfig().getId(),
                t.getPageType(),
                t.getStatus(),
                t.getTotalItems(),
                t.getCrawledItems(),
                t.getFailedItems(),
                t.getStartedAt(),
                t.getCompletedAt(),
                t.getErrorMessage()
        );
    }
}