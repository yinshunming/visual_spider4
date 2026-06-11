package com.visualspider.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrowserSessionStatus")
class BrowserSessionStatusTest {

    @Test
    @DisplayName("ACTIVE 与 CLOSED 都存在")
    void hasActiveAndClosed() {
        assertThat(BrowserSessionStatus.values())
                .contains(BrowserSessionStatus.ACTIVE, BrowserSessionStatus.CLOSED);
    }
}
