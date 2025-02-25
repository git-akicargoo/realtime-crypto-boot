package com.example.boot.web.websocket.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisWebSocketHandler extends TextWebSocketHandler {
    private final MarketAnalysisService marketAnalysisService;
    private final Map<WebSocketSession, AnalysisRequest> activeAnalysis = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received message: {}", message.getPayload());
        
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String command = jsonNode.get("command").asText();
        
        if ("start".equals(command)) {
            AnalysisRequest request = objectMapper.treeToValue(jsonNode.get("data"), AnalysisRequest.class);
            log.info("Starting analysis for request: {}", request);
            
            activeAnalysis.put(session, request);
            
            Disposable oldSubscription = subscriptions.remove(session);
            if (oldSubscription != null) {
                oldSubscription.dispose();
            }
            
            Disposable subscription = marketAnalysisService.startRealtimeAnalysis(request)
                .subscribe(
                    result -> {
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
                            }
                        } catch (Exception e) {
                            log.error("Failed to send analysis result", e);
                        }
                    },
                    error -> log.error("Error in analysis stream", error)
                );
            
            subscriptions.put(session, subscription);
            
        } else if ("stop".equals(command)) {
            log.info("Stopping analysis for session: {}", session.getId());
            stopAnalysis(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket closed: {}", session.getId());
        stopAnalysis(session);
    }

    private void stopAnalysis(WebSocketSession session) {
        Disposable subscription = subscriptions.remove(session);
        if (subscription != null) {
            subscription.dispose();
        }
        
        AnalysisRequest request = activeAnalysis.remove(session);
        if (request != null) {
            marketAnalysisService.stopRealtimeAnalysis(request);
        }
    }
} 