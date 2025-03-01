package com.example.boot.web.websocket.handler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.example.boot.web.controller.InfrastructureStatusController;
import com.example.boot.web.dto.StatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final MarketAnalysisService marketAnalysisService;
    private final InfrastructureStatusController infraStatus;
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
        // 인프라 상태 체크
        StatusResponse status = infraStatus.getStatus();
        if (!status.isValid()) {
            try {
                String errorMessage = objectMapper.writeValueAsString(Map.of(
                    "error", "Services unavailable",
                    "details", String.format("Required services are down (Redis: %s, Kafka: %s)", 
                        status.isRedisOk() ? "UP" : "DOWN",
                        status.isKafkaOk() ? "UP" : "DOWN")
                ));
                session.sendMessage(new TextMessage(errorMessage));
                return;
            } catch (IOException e) {
                log.error("Failed to send error message", e);
                return;
            }
        }

        stopRealtimeAnalysis(session);  // 기존 분석 중지
        
        // CurrencyPair 객체 생성
        CurrencyPair currencyPair = request.toCurrencyPair();
        if (currencyPair != null) {
            request.setCurrencyPair(currencyPair.toString());
        }
        
        activeAnalysis.put(session, request);

        Disposable subscription = marketAnalysisService.startRealtimeAnalysis(
            request.getExchange(),
            request.getCurrencyPair(),
            request.getPriceDropThreshold(),
            request.getVolumeIncreaseThreshold(),
            request.getSmaShortPeriod(),
            request.getSmaLongPeriod()
        ).onErrorResume(error -> {
            log.error("Analysis error", error);
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "error", "Analysis failed",
                    "details", error.getMessage()
                ))));
            } catch (IOException e) {
                log.error("Failed to send error message", e);
            }
            return Mono.empty();
        }).subscribe(result -> {
            try {
                // 응답에 symbol과 quoteCurrency 추가
                if (request.getSymbol() != null && request.getQuoteCurrency() != null) {
                    result = AnalysisResponse.builder()
                        .exchange(result.getExchange())
                        .currencyPair(result.getCurrencyPair())
                        .symbol(request.getSymbol())
                        .quoteCurrency(request.getQuoteCurrency())
                        .analysisTime(result.getAnalysisTime())
                        .currentPrice(result.getCurrentPrice())
                        .priceChangePercent(result.getPriceChangePercent())
                        .volumeChangePercent(result.getVolumeChangePercent())
                        .reboundProbability(result.getReboundProbability())
                        .analysisResult(result.getAnalysisResult())
                        .message(result.getMessage())
                        .sma1Difference(result.getSma1Difference())
                        .sma3Difference(result.getSma3Difference())
                        .smaBreakout(result.isSmaBreakout())
                        .rsiValue(result.getRsiValue())
                        .rsiSignal(result.getRsiSignal())
                        .bollingerUpper(result.getBollingerUpper())
                        .bollingerMiddle(result.getBollingerMiddle())
                        .bollingerLower(result.getBollingerLower())
                        .bollingerSignal(result.getBollingerSignal())
                        .bollingerWidth(result.getBollingerWidth())
                        .build();
                }
                
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