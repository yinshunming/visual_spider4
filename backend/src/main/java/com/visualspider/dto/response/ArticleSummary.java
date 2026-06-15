package com.visualspider.dto.response;

import com.visualspider.entity.Article;
import com.visualspider.enums.ItemStatus;

import java.time.Instant;
import java.util.Map;

public record ArticleSummary(
        Long id,
        Long configId,
        String url,
        ItemStatus status,
        Map<String, String> customFields,
        String errorMessage,
        Instant fetchedAt
) {
    public static ArticleSummary from(Article a) {
        return new ArticleSummary(
                a.getId(),
                a.getConfig() == null ? null : a.getConfig().getId(),
                a.getUrl(),
                a.getStatus(),
                a.getCustomFields(),
                a.getErrorMessage(),
                a.getFetchedAt()
        );
    }
}