package com.omniai.channel.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebChatAdapter webChatAdapter;
    private final String[] allowedOrigins;

    public WebSocketConfig(WebChatAdapter webChatAdapter,
                           @Value("${omniai.web.allowed-origins}") String[] allowedOrigins) {
        this.webChatAdapter = webChatAdapter;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webChatAdapter, "/ws/chat").setAllowedOrigins(allowedOrigins);
    }
}
