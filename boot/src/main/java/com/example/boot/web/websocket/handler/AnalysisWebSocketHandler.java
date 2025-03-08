package com.example.boot.web.websocket.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.example.boot.web.controller.InfrastructureStatusController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

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
        log.info("Starting real-time analysis for {} - {}", request.getExchange(), request.getCurrencyPair());
        
        // tradingStyle이 null인 경우 기본값 설정
        if (request.getTradingStyle() == null) {
            request.setTradingStyle("DAY_TRADING");
            log.info("Trading style was null, setting default to DAY_TRADING");
        }
        
        // 분석 서비스 호출
        Flux<AnalysisResponse> analysisFlux = marketAnalysisService.startRealtimeAnalysis(request);
        
        // 구독 및 결과 전송
        Disposable subscription = analysisFlux.subscribe(
            result -> {
                try {
                    // 응답 객체 생성
                    AnalysisResponse response = AnalysisResponse.builder()
                        .exchange(result.getExchange())
                        .currencyPair(result.getCurrencyPair())
                        .symbol(result.getSymbol())
                        .quoteCurrency(result.getQuoteCurrency())
                        .analysisTime(result.getAnalysisTime())
                        .currentPrice(result.getCurrentPrice())
                        .tradingStyle(result.getTradingStyle() != null ? result.getTradingStyle() : request.getTradingStyle())
                        .buySignalStrength(result.getBuySignalStrength())
                        .marketCondition(result.getMarketCondition())
                        .marketConditionStrength(result.getMarketConditionStrength())
                        .volumeSignalStrength(result.getVolumeSignalStrength())
                        .smaMediumDifference(result.getSmaMediumDifference())
                        .smaSignal(result.getSmaSignal())
                        .rsiValue(result.getRsiValue())
                        .rsiSignal(result.getRsiSignal())
                        .bollingerSignal(result.getBollingerSignal())
                        .bollingerWidth(result.getBollingerWidth())
                        .analysisResult(result.getAnalysisResult())
                        .message(result.getMessage())
                        .volumeChangePercent(result.getVolumeChangePercent())  // 이 부분만 추가
                        .priceChangePercent(result.getPriceChangePercent())
                        .build();
                    
                    // 디버깅을 위한 로깅 추가
                    if (result.getRsiSignal() == null || result.getBollingerSignal() == null || 
                        result.getSmaSignal() == null || result.getMarketCondition() == null ||
                        result.getBuySignalStrength() == 0.0 || result.getRsiValue() == 0.0) {
                        log.warn("분석 결과에 null 또는 0 값이 있습니다: rsiSignal={}, bollingerSignal={}, smaSignal={}, marketCondition={}, buySignalStrength={}, rsiValue={}",
                            result.getRsiSignal(), result.getBollingerSignal(), result.getSmaSignal(), 
                            result.getMarketCondition(), result.getBuySignalStrength(), result.getRsiValue());
                    }
                    
                    // JSON 변환 및 전송
                    String json = objectMapper.writeValueAsString(response);
                    session.sendMessage(new TextMessage(json));
                    
                } catch (Exception e) {
                    log.error("Error sending analysis result: ", e);
                }
            },
            error -> {
                log.error("Analysis error: ", error);
                try {
                    // 에러 메시지 전송
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Analysis failed: " + error.getMessage());
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
                } catch (Exception e) {
                    log.error("Error sending error message: ", e);
                }
            }
        );
        
        // 세션별 구독 정보 저장
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