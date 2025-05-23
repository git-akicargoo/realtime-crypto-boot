package com.example.boot.web.websocket.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.boot.web.websocket.handler.FrontendWebSocketHandler;

@Configuration
@EnableWebSocket
public class FrontendWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private FrontendWebSocketHandler frontendHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendHandler, "/ws/exchange")
               .setAllowedOrigins("*");  // 개발용
    }
} 