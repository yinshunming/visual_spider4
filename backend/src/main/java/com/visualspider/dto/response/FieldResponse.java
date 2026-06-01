package com.visualspider.dto.response;

import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;

import java.time.Instant;

public record FieldResponse(
        Long id,
        FieldPageType pageType,
        String fieldName,
        FieldType fieldType,
        String selector,
        Instant createdAt,
        Instant updatedAt
) {
    public static FieldResponse from(CrawlField field) {
        return new FieldResponse(
                field.getId(),
                field.getPageType(),
                field.getFieldName(),
                field.getFieldType(),
                field.getSelector(),
                field.getCreatedAt(),
                field.getUpdatedAt()
        );
    }
}
