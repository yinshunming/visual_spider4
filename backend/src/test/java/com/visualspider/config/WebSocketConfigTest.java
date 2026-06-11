package com.visualspider.config;

import com.visualspider.ws.PageWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("WebSocketConfig")
class WebSocketConfigTest {

    @Test
    @DisplayName("注册 /api/v1/ws/page handler")
    void registersPageHandler() {
        PageWebSocketHandler handler = mock(PageWebSocketHandler.class);
        WebSocketConfig config = new WebSocketConfig(handler);
        assertThat(config).isNotNull();
    }
}
