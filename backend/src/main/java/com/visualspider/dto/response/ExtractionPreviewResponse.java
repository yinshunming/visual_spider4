package com.visualspider.dto.response;

import java.util.List;

public record ExtractionPreviewResponse(
        List<FieldPreviewResult> fields,
        List<String> warnings
) {
}
