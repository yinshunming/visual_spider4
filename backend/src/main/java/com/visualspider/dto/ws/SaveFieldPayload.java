package com.visualspider.dto.ws;

import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;

public record SaveFieldPayload(FieldPageType pageType, String fieldName, FieldType fieldType, String selector) {
}
