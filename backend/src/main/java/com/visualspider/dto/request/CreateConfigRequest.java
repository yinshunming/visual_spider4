package com.visualspider.dto.request;

import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建配置请求体。name / startUrl / pageType / selectorType 必填;
 * status 缺省时由 service 默认填 STOPPED。
 */
public record CreateConfigRequest(
        @NotBlank String name,
        @NotBlank String startUrl,
        PageType pageType,
        SelectorType selectorType,
        com.visualspider.enums.ConfigStatus status
) {
}