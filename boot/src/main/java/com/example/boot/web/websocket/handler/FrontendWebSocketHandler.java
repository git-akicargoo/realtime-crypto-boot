package com.example.boot.web.websocket.handler;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FrontendWebSocketHandler extends TextWebSocketHandler {

    private final DistributionService distributionService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public FrontendWebSocketHandler(DistributionService distributionService) {
        this.distributionService = distributionService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        log.debug("Frontend WebSocket connected: {}", sessionId);
        sessions.put(sessionId, session);
        
        distributionService.startDistribution()
            .doOnSubscribe(s -> log.debug("Starting to receive distribution data for session: {}", sessionId))
            .doOnNext(data -> log.debug("Received data for session {}: {}", sessionId, data))
            .subscribe(data -> {
                if (session.isOpen()) {
                    try {
                        String jsonData = objectMapper.writeValueAsString(data);
                        session.sendMessage(new TextMessage(jsonData));
                    } catch (Exception e) {
                        handleSessionError(session, e);
                    }
                } else {
                    removeSession(session);
                }
            }, 
            error -> handleError(session, error),
            () -> log.debug("Distribution completed for session: {}", sessionId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Frontend WebSocket disconnected: {}, status: {}", session.getId(), status);
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Frontend WebSocket error for session {}: {}", session.getId(), exception.getMessage());
        handleSessionError(session, exception);
    }

    private void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        log.debug("Removed session: {}", session.getId());
    }

    private void handleSessionError(WebSocketSession session, Throwable error) {
        log.error("Error in session {}: {}", session.getId(), error.getMessage());
        if (error instanceof IllegalStateException && error.getMessage().contains("closed")) {
            removeSession(session);
        }
    }

    private void handleError(WebSocketSession session, Throwable error) {
        log.error("Error in distribution for session {}: {}", session.getId(), error.getMessage());
        removeSession(session);
    }
} 