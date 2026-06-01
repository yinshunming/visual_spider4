package com.visualspider.dto.request;

import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;

public record CreateConfigRequest(
        String name,
        PageType pageType,
        SelectorType selectorType
) {
}
