package com.example.boot.web.websocket.handler;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FrontendWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ExchangeDataIntegrationService exchangeService;
    
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public FrontendWebSocketHandler() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())  // JSR310 모듈 등록
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);  // ISO-8601 형식으로 출력
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("Frontend WebSocket connected: {}", session.getId());
        sessions.put(session.getId(), session);
        
        exchangeService.subscribe()
            .doOnSubscribe(s -> log.debug("Starting to subscribe to exchange data"))
            .doOnNext(data -> log.debug("Received data from exchange: {}", data))
            .subscribe(data -> {
                if (session.isOpen()) {
                    try {
                        String jsonData = objectMapper.writeValueAsString(data);
                        log.debug("Sending data to frontend: {}", jsonData);
                        session.sendMessage(new TextMessage(jsonData));
                    } catch (Exception e) {
                        if (e instanceof IllegalStateException && e.getMessage().contains("closed")) {
                            log.warn("Session {} was closed", session.getId());
                        } else {
                            log.error("Failed to send data to client: {}", e.getMessage());
                        }
                        sessions.remove(session.getId());
                    }
                } else {
                    log.debug("Session {} is already closed, removing", session.getId());
                    sessions.remove(session.getId());
                }
            }, 
            error -> log.error("Error in subscription: ", error),
            () -> log.debug("Subscription completed"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Frontend WebSocket disconnected: {}, status: {}", session.getId(), status);
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Frontend WebSocket error: {}", exception.getMessage());
    }
} 