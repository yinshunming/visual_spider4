package com.visualspider.dto.request;

import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;

public record CreateFieldRequest(
        FieldPageType pageType,
        String fieldName,
        FieldType fieldType,
        String selector
) {
}
