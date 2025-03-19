package com.example.boot.web.websocket.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.CryptoAnalysisService;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisManager;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisSessionRegistry;
import com.example.boot.web.controller.InfrastructureStatusController;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisStompController {
    
    private final CryptoAnalysisService analysisService;
    private final InfrastructureStatusController infraStatus;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisManager analysisManager;
    private final AnalysisSessionRegistry analysisSessionRegistry;

    @MessageMapping("/analysis.start")
    public void startAnalysis(@Payload AnalysisRequest request, SimpMessageHeaderAccessor headerAccessor) {
        log.debug("Received analysis request: {}", request);
        
        // 세션 ID 가져오기
        String sessionId = headerAccessor.getSessionId();
        log.debug("Session ID for analysis request: {}", sessionId);
        
        // 인프라 상태 확인
        var status = infraStatus.getStatus();
        if (!status.isValid()) {
            log.warn("Infrastructure not ready, status: {}", status);
            messagingTemplate.convertAndSend("/topic/analysis.error", 
                "Trading infrastructure is not ready. Please try again later.");
            return;
        }
        
        // 카드 ID 생성 또는 요청에서 가져오기
        final String cardId = request.getCardId() != null && !request.getCardId().isEmpty() ?
                request.getCardId() : generateCardId(request.getExchange(), request.getCurrencyPair());
        
        // cardId가 생성되었으면 요청에 설정
        if (request.getCardId() == null || request.getCardId().isEmpty()) {
            request.setCardId(cardId);
            request.setTimestamp(System.currentTimeMillis());
        }
        
        log.info("Starting analysis for {}-{}, style: {}, cardId: {}, sessionId: {}", 
                request.getExchange(), request.getCurrencyPair(), request.getTradingStyle(), 
                cardId, sessionId);
        
        // 분석 요청 등록
        analysisManager.registerAnalysis(cardId, request, sessionId);
                
        analysisService.startAnalysis(request)
            .subscribe(
                response -> {
                    try {
                        // 유효한 분석인지 확인
                        if (!analysisManager.isValidAnalysis(cardId)) {
                            log.info("Skipping response for invalid session: cardId={}", cardId);
                            return;
                        }
                        
                        // JSON 그대로 로그 출력 (개발 환경에서만)
                        if (log.isDebugEnabled()) {
                            String jsonResponse = objectMapper.writeValueAsString(response);
                            log.debug("Analysis response JSON: {}", jsonResponse);
                        }
                        
                        messagingTemplate.convertAndSend("/topic/analysis", response);
                        log.debug("Analysis response sent to /topic/analysis");
                    } catch (Exception e) {
                        log.error("Error processing response: {}", e.getMessage(), e);
                    }
                },
                error -> {
                    if (error instanceof CancellationException) {
                        log.info("Analysis cancelled due to disconnection for cardId: {}", cardId);
                    } else {
                        log.error("Error in analysis stream: {}", error.getMessage(), error);
                        messagingTemplate.convertAndSend("/topic/analysis.error", 
                                Map.of("cardId", cardId, "error", error.getMessage()));
                    }
                    
                    // 에러 발생 시 분석 요청 제거
                    analysisManager.unregisterAnalysis(cardId);
                },
                () -> {
                    log.info("Analysis stream completed for {}-{}, cardId: {}", 
                            request.getExchange(), request.getCurrencyPair(), cardId);
                    // 분석 완료 시 세션-분석 요청 매핑 제거
                    analysisManager.unregisterAnalysis(cardId);
                }
            );
        
        // 테스트 메시지 전송
        AnalysisResponse testResponse = AnalysisResponse.builder()
            .exchange(request.getExchange())
            .currencyPair(request.getCurrencyPair())
            .cardId(cardId)
            .message("테스트 메시지 - 분석 시작됨")
            .build();
        
        log.debug("Sending test message to /topic/analysis");
        messagingTemplate.convertAndSend("/topic/analysis", testResponse);
    }

    @MessageMapping("/analysis.stop")
    public void stopAnalysis(@Payload AnalysisRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String cardId = request.getCardId();
        String sessionId = headerAccessor.getSessionId();
        
        log.info("Stopping analysis for {}-{}, cardId: {}, sessionId: {}", 
                request.getExchange(), request.getCurrencyPair(), cardId, sessionId);
        
        // 분석 중지 및 등록 해제
        analysisManager.unregisterAnalysis(cardId);
        
        // 중지 메시지 전송
        messagingTemplate.convertAndSend("/topic/analysis.stop", 
            Map.of("cardId", cardId, "message", 
                String.format("Analysis stopped for %s-%s", request.getExchange(), request.getCurrencyPair())));
    }
    
    /**
     * 카드 ID 생성
     */
    private String generateCardId(String exchange, String currencyPair) {
        String baseCardId = (exchange + "-" + currencyPair).toLowerCase();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return baseCardId + "-" + uuid;
    }
} 