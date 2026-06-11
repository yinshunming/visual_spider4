package com.visualspider.config;

import com.visualspider.ws.PageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PageWebSocketHandler pageHandler;

    public WebSocketConfig(PageWebSocketHandler pageHandler) {
        this.pageHandler = pageHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pageHandler, "/api/v1/ws/page")
                .setAllowedOriginPatterns("*");
    }
}
