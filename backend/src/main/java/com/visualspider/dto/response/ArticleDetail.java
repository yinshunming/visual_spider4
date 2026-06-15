package com.visualspider.dto.response;

import com.visualspider.entity.Article;
import com.visualspider.enums.ItemStatus;

import java.time.Instant;
import java.util.Map;

public record ArticleDetail(
        Long id,
        Long configId,
        String url,
        String rawHtml,
        Map<String, String> customFields,
        ItemStatus status,
        String errorMessage,
        Instant fetchedAt
) {
    public static ArticleDetail from(Article a) {
        return new ArticleDetail(
                a.getId(),
                a.getConfig() == null ? null : a.getConfig().getId(),
                a.getUrl(),
                a.getRawHtml(),
                a.getCustomFields(),
                a.getStatus(),
                a.getErrorMessage(),
                a.getFetchedAt()
        );
    }
}