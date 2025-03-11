package com.example.boot.web.websocket.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.boot.web.websocket.handler.AnalysisWebSocketHandler;
import com.example.boot.web.websocket.handler.FrontendWebSocketHandler;
import com.example.boot.web.websocket.handler.SimulTradingWebSocketHandler;

@Configuration
@EnableWebSocket
public class FrontendWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private FrontendWebSocketHandler frontendHandler;
    
    @Autowired
    private AnalysisWebSocketHandler analysisHandler;
    
    @Autowired
    private SimulTradingWebSocketHandler simulTradingHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendHandler, "/ws/exchange")
               .setAllowedOrigins("*");  // 개발용
               
        registry.addHandler(analysisHandler, "/ws/analysis")
               .setAllowedOrigins("*");
        
        registry.addHandler(simulTradingHandler, "/ws/simul-trading")
                .setAllowedOrigins("*");
    }
} 