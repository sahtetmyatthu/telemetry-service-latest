package org.mdt.telemetry_service.config;

import org.mdt.telemetry_service.service.WebSocketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketService webSocketService;

    public WebSocketConfig(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketService, "/telemetry")
                .setAllowedOrigins("*");

        registry.addHandler(webSocketService, "/telemetry/{port}")
                .setAllowedOrigins("*");
    }
}
