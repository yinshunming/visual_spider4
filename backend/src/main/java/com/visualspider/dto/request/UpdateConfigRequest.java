package com.visualspider.dto.request;

import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;

import java.util.List;

public record UpdateConfigRequest(
        String name,
        PageType pageType,
        SelectorType selectorType,
        List<CreateFieldRequest> fields
) {
}
