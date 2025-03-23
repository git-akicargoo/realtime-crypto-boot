package com.example.boot.exchange.layer6_analysis.websocket.handler;

import java.util.Map;
import java.util.concurrent.CancellationException;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.CryptoAnalysisService;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisManager;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 분석 WebSocket 핸들러
 * 분석 시작/중지 및 메시지 전송을 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStompHandler {

    private final CryptoAnalysisService analysisService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisManager analysisManager;

    /**
     * 분석 시작
     * 
     * @param request   분석 요청 객체
     * @param sessionId 세션 ID
     */
    public void startAnalysis(AnalysisRequest request, String sessionId) {
        // 카드 ID가 없는 경우 생성
        if (request.getCardId() == null || request.getCardId().isEmpty()) {
            String generatedCardId = generateCardId(request.getExchange(), request.getCurrencyPair());
            request.setCardId(generatedCardId);
        }
        
        final String cardId = request.getCardId();
        
        // 이미 동일한 분석이 실행 중인지 확인
        if (isDuplicateAnalysis(request)) {
            log.warn("Duplicate analysis request detected for {}-{}", 
                    request.getExchange(), request.getCurrencyPair());
            sendErrorMessage(cardId, "이미 동일한 거래소와 코인에 대한 분석이 실행 중입니다.");
            return;
        }
        
        // 이미 실행 중인 카드에 대한 요청인지 확인
        if (isCardAlreadyRunning(cardId)) {
            log.info("Analysis already running for cardId: {}, restarting analysis", cardId);
            // 기존 분석 중지 후 재시작
            stopAnalysis(cardId, request.getExchange(), request.getCurrencyPair());
        }
        
        log.info("Starting analysis for {}-{}, style: {}, cardId: {}, sessionId: {}", 
                request.getExchange(), request.getCurrencyPair(), request.getTradingStyle(), 
                cardId, sessionId);
        
        // 분석 요청 등록
        analysisManager.registerAnalysis(cardId, request, sessionId);
        
        // 초기 응답 전송
        sendInitialResponse(cardId, request);
        
        // 분석 구독 시작
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
                        
                        // 응답 전송
                        sendAnalysisResponse(cardId, response);
                    } catch (Exception e) {
                        log.error("Error processing response: {}", e.getMessage(), e);
                        sendErrorMessage(cardId, "응답 처리 중 오류 발생: " + e.getMessage());
                    }
                },
                error -> {
                    if (error instanceof CancellationException) {
                        log.info("Analysis cancelled due to disconnection for cardId: {}", cardId);
                    } else {
                        log.error("Error in analysis stream: {}", error.getMessage(), error);
                        sendErrorMessage(cardId, "분석 중 오류 발생: " + error.getMessage());
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
    }

    /**
     * 분석 중지
     * 
     * @param cardId        카드 ID
     * @param exchange      거래소
     * @param currencyPair  통화쌍
     */
    public void stopAnalysis(String cardId, String exchange, String currencyPair) {
        log.info("Stopping analysis for {}-{}, cardId: {}", exchange, currencyPair, cardId);
        
        // 분석 중지 및 등록 해제
        analysisManager.unregisterAnalysis(cardId);
        
        // 중지 메시지 전송
        sendStopMessage(cardId, exchange, currencyPair);
    }
    
    /**
     * 이전 컨트롤러와의 호환성을 위한 분석 중지 메서드
     * 
     * @param request   분석 요청 객체
     * @param sessionId 세션 ID
     */
    public void stopAnalysis(AnalysisRequest request, String sessionId) {
        String cardId = request.getCardId();
        String exchange = request.getExchange();
        String currencyPair = request.getCurrencyPair();
        
        log.info("Stopping analysis (legacy method) for {}-{}, cardId: {}, sessionId: {}", 
                exchange, currencyPair, cardId, sessionId);
        
        stopAnalysis(cardId, exchange, currencyPair);
    }

    /**
     * 분석 결과 전송
     * 
     * @param cardId    카드 ID
     * @param response  분석 응답 객체
     */
    public void sendAnalysisResponse(String cardId, AnalysisResponse response) {
        // 카드 ID별로 별도 토픽으로 메시지 전송
        String cardSpecificTopic = "/topic/analysis." + cardId;
        messagingTemplate.convertAndSend(cardSpecificTopic, response);
        log.debug("Analysis response sent to {}", cardSpecificTopic);
        
        // 하위 호환성을 위해 공통 토픽에도 함께 전송
        messagingTemplate.convertAndSend("/topic/analysis", response);
    }

    /**
     * 오류 메시지 전송
     * 
     * @param cardId    카드 ID
     * @param message   오류 메시지
     */
    public void sendErrorMessage(String cardId, String message) {
        if (cardId == null || cardId.isEmpty()) {
            cardId = "unknown";
        }
        
        Map<String, String> errorMessage = Map.of(
            "cardId", cardId, 
            "error", message
        );
        
        String errorTopic = "/topic/analysis.error." + cardId;
        messagingTemplate.convertAndSend(errorTopic, errorMessage);
        
        // 하위 호환성을 위해 공통 토픽에도 전송
        messagingTemplate.convertAndSend("/topic/analysis.error", errorMessage);
    }
    
    /**
     * 이전 버전과의 호환성을 위한 오류 메시지 전송 메서드
     * 
     * @param request   분석 요청 객체
     * @param message   오류 메시지
     * @param sessionId 세션 ID
     */
    public void sendErrorMessage(AnalysisRequest request, String message, String sessionId) {
        String cardId = request.getCardId();
        log.info("Sending error message (legacy method) for cardId: {}, sessionId: {}: {}", 
                cardId, sessionId, message);
        
        sendErrorMessage(cardId, message);
    }

    /**
     * 초기 응답 전송
     * 
     * @param cardId    카드 ID
     * @param request   분석 요청 객체
     */
    public void sendInitialResponse(String cardId, AnalysisRequest request) {
        // 테스트 메시지 전송 - 카드 ID별 토픽으로 전송
        AnalysisResponse initialResponse = AnalysisResponse.builder()
            .exchange(request.getExchange())
            .currencyPair(request.getCurrencyPair())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .cardId(cardId)
            .message("분석 시작됨 - 데이터 수집 중...")
            .analysisResult("WAITING_FOR_DATA")
            .timestamp(System.currentTimeMillis())
            .tradingStyle(request.getTradingStyle())
            .build();
        
        sendAnalysisResponse(cardId, initialResponse);
    }

    /**
     * 중지 메시지 전송
     * 
     * @param cardId        카드 ID
     * @param exchange      거래소
     * @param currencyPair  통화쌍
     */
    public void sendStopMessage(String cardId, String exchange, String currencyPair) {
        // 중지 메시지 전송 - 카드 ID별 토픽으로 전송
        Map<String, String> stopMessage = Map.of(
            "cardId", cardId, 
            "message", String.format("Analysis stopped for %s-%s", exchange, currencyPair)
        );
        
        String cardSpecificTopic = "/topic/analysis.stop." + cardId;
        messagingTemplate.convertAndSend(cardSpecificTopic, stopMessage);
        
        // 하위 호환성을 위해 공통 토픽에도 함께 전송
        messagingTemplate.convertAndSend("/topic/analysis.stop", stopMessage);
    }
    
    /**
     * 중복 분석 요청 확인
     * 
     * @param request 분석 요청 객체
     * @return 중복 여부
     */
    private boolean isDuplicateAnalysis(AnalysisRequest request) {
        String requestKey = (request.getExchange() + "-" + request.getCurrencyPair()).toLowerCase();
        
        // 활성 분석 중에 동일한 거래소-통화쌍이 있는지 확인
        return analysisManager.getAllActiveRequests().stream()
            .filter(req -> !req.getCardId().equals(request.getCardId())) // 자기 자신 제외
            .anyMatch(req -> {
                String key = (req.getExchange() + "-" + req.getCurrencyPair()).toLowerCase();
                return key.equals(requestKey);
            });
    }
    
    /**
     * 이미 실행 중인 카드인지 확인
     * 
     * @param cardId 카드 ID
     * @return 실행 중 여부
     */
    private boolean isCardAlreadyRunning(String cardId) {
        return analysisManager.isCardActive(cardId);
    }
    
    /**
     * 카드 ID 생성
     * 
     * @param exchange     거래소
     * @param currencyPair 거래쌍
     * @return 생성된 카드 ID
     */
    private String generateCardId(String exchange, String currencyPair) {
        return (exchange + "-" + currencyPair).toLowerCase() + "-" + 
                Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
    }
} 