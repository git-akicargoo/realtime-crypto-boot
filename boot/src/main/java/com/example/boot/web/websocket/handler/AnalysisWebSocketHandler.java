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
import com.example.boot.exchange.layer6_analysis.service.AnalysisDataSharingService;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.example.boot.web.controller.InfrastructureStatusController;
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
    private final AnalysisDataSharingService dataSharingService;
    private final Map<WebSocketSession, AnalysisRequest> activeAnalysis = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Disposable> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // 메시지를 DTO 객체로 직접 변환
            AnalysisRequest request = objectMapper.readValue(message.getPayload(), AnalysisRequest.class);
            String action = request.getAction();
            
            if ("startAnalysis".equals(action)) {
                // 인프라 상태 확인
                var status = infraStatus.getStatus();
        if (!status.isValid()) {
                    sendErrorMessage(session, "Trading infrastructure is not ready. Please try again later.");
                return;
            }
                
                // 분석 시작
                log.info("Starting analysis for {}-{}, style: {}", 
                         request.getExchange(), request.getCurrencyPair(), request.getTradingStyle());
                
                // 카드 ID 생성 (백엔드에서 생성)
                String baseCardId = (request.getExchange() + "-" + request.getCurrencyPair()).toLowerCase();
                // UUID 생성 (8자리 랜덤 문자열)
                String uuid = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
                // 최종 카드 ID 생성 (baseCardId-uuid)
                String cardId = baseCardId + "-" + uuid;
                // 타임스탬프 생성 (백엔드에서 생성)
                long timestamp = System.currentTimeMillis();
                
                // 상세 로그 추가: 카드 ID 생성 정보
                log.info("백엔드에서 생성된 기본 카드 ID: {}", baseCardId);
                log.info("백엔드에서 생성된 UUID: {}", uuid);
                log.info("백엔드에서 생성된 최종 카드 ID: {}", cardId);
                log.info("백엔드에서 생성된 타임스탬프: {}", timestamp);
                
                // 요청 객체에 카드 ID와 타임스탬프 설정
                request.setCardId(cardId);
                request.setTimestamp(timestamp);
                
                // 기존 분석 저장
        activeAnalysis.put(session, request);

                // 추가: 분석 요청 정보를 공유 서비스에 저장
                dataSharingService.saveAnalysisRequest(request);

                // 분석 시작 및 응답 구독
                Flux<AnalysisResponse> flux = marketAnalysisService.startRealtimeAnalysis(request);
                
                // 분석 결과 처리
                Disposable subscription = flux.subscribe(
                    response -> {
                        try {
                            // 새 응답 객체 생성하여 카드 ID 정보 포함
                            AnalysisResponse responseWithCardInfo = AnalysisResponse.builder()
                                .exchange(response.getExchange())
                                .currencyPair(response.getCurrencyPair())
                                .symbol(response.getSymbol())
                                .quoteCurrency(response.getQuoteCurrency())
                                .analysisTime(response.getAnalysisTime())
                                .currentPrice(response.getCurrentPrice())
                                .priceChangePercent(response.getPriceChangePercent())
                                .volumeChangePercent(response.getVolumeChangePercent())
                                .reboundProbability(response.getReboundProbability())
                                .analysisResult(response.getAnalysisResult())
                                .message(response.getMessage())
                                .tradingStyle(response.getTradingStyle())
                                .buySignalStrength(response.getBuySignalStrength())
                                .sma1Difference(response.getSma1Difference())
                                .smaMediumDifference(response.getSmaMediumDifference())
                                .sma3Difference(response.getSma3Difference())
                                .smaBreakout(response.isSmaBreakout())
                                .smaSignal(response.getSmaSignal())
                                .rsiValue(response.getRsiValue())
                                .rsiSignal(response.getRsiSignal())
                                .bollingerUpper(response.getBollingerUpper())
                                .bollingerMiddle(response.getBollingerMiddle())
                                .bollingerLower(response.getBollingerLower())
                                .bollingerSignal(response.getBollingerSignal())
                                .bollingerWidth(response.getBollingerWidth())
                                .volumeSignalStrength(response.getVolumeSignalStrength())
                                .marketCondition(response.getMarketCondition())
                                .marketConditionStrength(response.getMarketConditionStrength())
                                // 카드 ID와 타임스탬프 추가
                                .cardId(cardId)
                                .timestamp(timestamp)
                                .build();
                            
                            // 응답 전송
                            String responseJson = objectMapper.writeValueAsString(responseWithCardInfo);
                            
                            // 상세 로그 추가: 프론트엔드로 전송되는 응답 데이터
                            log.info("프론트엔드로 전송되는 응답 카드 ID: {}", responseWithCardInfo.getCardId());
                            log.info("프론트엔드로 전송되는 응답 타임스탬프: {}", responseWithCardInfo.getTimestamp());
                            
                            session.sendMessage(new TextMessage(responseJson));
                            
                            // 추가: 공유 서비스에 데이터 업데이트
                            dataSharingService.updateAnalysisData(cardId, responseWithCardInfo);
                        } catch (Exception e) {
                            log.error("Error sending analysis response", e);
                        }
                    },
                    error -> {
                        log.error("Error in analysis stream", error);
                        sendErrorMessage(session, "Analysis error: " + error.getMessage());
                        stopRealtimeAnalysis(session);
                    },
                    () -> {
                        log.info("Analysis stream completed for session: {}", session.getId());
                        stopRealtimeAnalysis(session);
                    }
                );
                
                // 구독 저장
                subscriptions.put(session, subscription);
            } else if ("stopAnalysis".equals(action)) {
                // 분석 중지
                stopRealtimeAnalysis(session);
                log.info("Analysis stopped for session: {}", session.getId());
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendErrorMessage(session, "Error processing request: " + e.getMessage());
        }
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
            
            // 추가: 공유 서비스에서 데이터 제거
            dataSharingService.removeCard(request.getCardId());
        }
    }

    private void sendErrorMessage(WebSocketSession session, String message) {
        try {
            // 에러 메시지 전송
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", message);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        } catch (Exception e) {
            log.error("Error sending error message: ", e);
        }
    }
} 