package com.visualspider.dto.response;

import com.visualspider.enums.FieldPreviewStatus;
import com.visualspider.enums.FieldType;

import java.util.List;

public record FieldPreviewResult(
        Long fieldId,
        String fieldName,
        FieldType fieldType,
        String selector,
        int matchCount,
        List<String> rawValues,
        List<String> validatedValues,
        FieldPreviewStatus status,
        String message
) {
}
