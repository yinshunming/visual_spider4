package com.visualspider.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PageFetchRequest(
        @NotBlank(message = "URL 不能为空") String url
) {
}
