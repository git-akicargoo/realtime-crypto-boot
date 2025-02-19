package com.example.boot.web.websocket.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer4_distribution.common.factory.DistributionServiceFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Component
public class FrontendWebSocketHandler extends TextWebSocketHandler {

    private static final AtomicInteger ACTIVE_SESSIONS = new AtomicInteger(0);
    private final DistributionServiceFactory distributionServiceFactory;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    public FrontendWebSocketHandler(DistributionServiceFactory distributionServiceFactory) {
        this.distributionServiceFactory = distributionServiceFactory;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("Frontend WebSocket connected: {}", sessionId);
        
        // 이전 세션이 있다면 정리
        if (sessions.containsKey(sessionId)) {
            removeSession(sessions.get(sessionId));
        }
        
        sessions.put(sessionId, session);
        ACTIVE_SESSIONS.incrementAndGet();
        
        Disposable subscription = distributionServiceFactory.getCurrentService()
            .startDistribution()
            .subscribe(data -> {
                if (session.isOpen()) {
                    try {
                        String jsonData = objectMapper.writeValueAsString(data);
                        session.sendMessage(new TextMessage(jsonData));
                        log.debug("Sent data to client {}: {}", sessionId, data);
                    } catch (Exception e) {
                        handleSessionError(session, e);
                    }
                } else {
                    removeSession(session);
                }
            }, 
            error -> handleError(session, error),
            () -> log.debug("Distribution completed for session: {}", sessionId));
            
        subscriptions.put(sessionId, subscription);
        log.info("Session started: {} (Active sessions: {})", sessionId, ACTIVE_SESSIONS.get());
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
        String sessionId = session.getId();
        if (sessions.remove(sessionId) != null) {
            ACTIVE_SESSIONS.decrementAndGet();
            
            // 구독 취소
            Disposable subscription = subscriptions.remove(sessionId);
            if (subscription != null) {
                subscription.dispose();
                log.debug("Cancelled subscription for session: {}", sessionId);
            }
            
            log.info("Session removed: {} (Active sessions: {})", 
                sessionId, ACTIVE_SESSIONS.get());
        }
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

    public static int getActiveSessionCount() {
        return ACTIVE_SESSIONS.get();
    }
} 