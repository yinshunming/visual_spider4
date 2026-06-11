package com.visualspider.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SelectorType")
class SelectorTypeTest {

    @Test
    @DisplayName("CSS 与 XPATH 都存在")
    void hasCssAndXpath() {
        assertThat(SelectorType.values())
                .contains(SelectorType.CSS, SelectorType.XPATH);
    }
}
