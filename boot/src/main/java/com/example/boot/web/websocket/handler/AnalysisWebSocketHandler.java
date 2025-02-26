package com.example.boot.web.websocket.handler;

import java.io.IOException;
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
    private final ObjectMapper objectMapper;
    private final MarketAnalysisService marketAnalysisService;
    private final Map<WebSocketSession, AnalysisRequest> activeAnalysis = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Disposable> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String command = jsonNode.get("command").asText();
        JsonNode data = jsonNode.get("data");

        if ("start".equals(command)) {
            AnalysisRequest request = objectMapper.treeToValue(data, AnalysisRequest.class);
            startRealtimeAnalysis(session, request);
        } else if ("stop".equals(command)) {
            stopRealtimeAnalysis(session);
        }
    }

    private void startRealtimeAnalysis(WebSocketSession session, AnalysisRequest request) {
        stopRealtimeAnalysis(session);  // 기존 분석 중지
        activeAnalysis.put(session, request);

        Disposable subscription = marketAnalysisService.startRealtimeAnalysis(
            request.getExchange(),
            request.getCurrencyPair(),
            request.getPriceDropThreshold(),
            request.getVolumeIncreaseThreshold(),
            request.getSmaShortPeriod(),    // 추가
            request.getSmaLongPeriod()      // 추가
        ).subscribe(result -> {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
            } catch (IOException e) {
                log.error("Failed to send analysis result", e);
            }
        });

        subscriptions.put(session, subscription);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket closed: {}", session.getId());
        stopRealtimeAnalysis(session);
    }

    private void stopRealtimeAnalysis(WebSocketSession session) {
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