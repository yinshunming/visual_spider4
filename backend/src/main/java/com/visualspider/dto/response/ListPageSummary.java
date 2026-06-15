package com.visualspider.dto.response;

import com.visualspider.entity.ListPage;

import java.time.Instant;

public record ListPageSummary(
        Long id,
        Long configId,
        Long taskId,
        String url,
        Instant fetchedAt
) {
    public static ListPageSummary from(ListPage p) {
        return new ListPageSummary(
                p.getId(),
                p.getConfig() == null ? null : p.getConfig().getId(),
                p.getTask() == null ? null : p.getTask().getId(),
                p.getUrl(),
                p.getFetchedAt()
        );
    }
}