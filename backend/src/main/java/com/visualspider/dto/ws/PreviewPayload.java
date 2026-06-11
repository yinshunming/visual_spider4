package com.visualspider.dto.ws;

import com.visualspider.enums.SelectorType;

public record PreviewPayload(SelectorType selectorType, String selector) {
}
