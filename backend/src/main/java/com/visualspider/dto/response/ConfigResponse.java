package com.visualspider.dto.response;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;

import java.time.Instant;
import java.util.List;

public record ConfigResponse(
        Long id,
        String name,
        String startUrl,
        PageType pageType,
        SelectorType selectorType,
        ConfigStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<FieldResponse> fields
) {
    public static ConfigResponse from(CrawlConfig config) {
        List<FieldResponse> fieldResponses = config.getFields() == null
                ? List.of()
                : config.getFields().stream().map(FieldResponse::from).toList();
        return new ConfigResponse(
                config.getId(),
                config.getName(),
                config.getStartUrl(),
                config.getPageType(),
                config.getSelectorType(),
                config.getStatus(),
                config.getCreatedAt(),
                config.getUpdatedAt(),
                fieldResponses
        );
    }
}
