package com.example.boot.web.card.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.CryptoAnalysisService;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisManager;
import com.example.boot.exchange.layer6_analysis.websocket.handler.AnalysisStompHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카드 서비스
 * 분석 카드의 생성, 조회, 수정, 삭제 기능을 제공합니다.
 * 메모리 기반으로 동작합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final AnalysisManager analysisManager;
    private final CryptoAnalysisService analysisService;
    private final AnalysisStompHandler analysisStompHandler;
    
    // 메모리 기반 카드 저장소 (카드 ID -> 분석 요청)
    private final Map<String, AnalysisRequest> cardStore = new ConcurrentHashMap<>();
    
    // 카드 상태 저장소
    private final Map<String, String> cardStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Double> cardPriceMap = new ConcurrentHashMap<>();
    private final Map<String, String> cardTimestampMap = new ConcurrentHashMap<>();
    
    /**
     * 카드 생성 및 필요시 분석 시작
     *
     * @param request 분석 요청 객체
     * @param sessionId 세션 ID
     * @param startAnalysis 분석 시작 여부
     * @return 생성된 카드 요청 객체
     */
    public AnalysisRequest createCard(AnalysisRequest request, String sessionId, boolean startAnalysis) {
        log.info("Creating card for {} - {}, startAnalysis: {}", 
                request.getExchange(), request.getCurrencyPair(), startAnalysis);
        
        // 카드 ID 생성 또는 요청에서 가져오기
        String cardId = request.getCardId();
        if (cardId == null || cardId.isEmpty()) {
            cardId = generateCardId(request.getExchange(), request.getCurrencyPair());
            request.setCardId(cardId);
        }
        
        if (request.getTimestamp() <= 0) {
            request.setTimestamp(System.currentTimeMillis());
        }
        
        // 메모리에 저장
        cardStore.put(cardId, request);
        
        // 초기 상태 설정
        String initialStatus = startAnalysis ? "RUNNING" : "CREATED";
        cardStatusMap.put(cardId, initialStatus);
        cardTimestampMap.put(cardId, LocalDateTime.now().toString());
        
        // 분석 액션 설정 (필요시)
        request.setAction(initialStatus);
        
        // 필요한 경우 분석 시작
        if (startAnalysis) {
            // 분석 세션 등록
            analysisManager.registerAnalysis(cardId, request, sessionId);
            
            // 분석 시작
            analysisService.startAnalysis(request);
        }
        
        return request;
    }
    
    /**
     * 카드 상태 업데이트 (분석 시작/중지)
     *
     * @param cardId 카드 ID
     * @param status 새 상태 (RUNNING, STOPPED 등)
     * @param sessionId 세션 ID
     * @return 업데이트된 카드
     */
    public AnalysisRequest updateCardStatus(String cardId, String status, String sessionId) {
        log.info("Updating card status: {} -> {}", cardId, status);
        
        // 카드 조회
        AnalysisRequest card = getCard(cardId);
        if (card == null) {
            log.warn("Card not found: {}", cardId);
            return null;
        }
        
        // 현재 상태
        String currentStatus = cardStatusMap.get(cardId);
        
        // 상태가 같으면 아무것도 하지 않음
        if (status.equals(currentStatus)) {
            log.info("Card {} already in status {}", cardId, status);
            return card;
        }
        
        // 상태에 따른 액션 수행
        if ("RUNNING".equals(status) && !"RUNNING".equals(currentStatus)) {
            // 분석 시작
            analysisManager.registerAnalysis(cardId, card, sessionId);
            analysisService.startAnalysis(card);
            log.info("Analysis started for card: {}", cardId);
        } else if ("STOPPED".equals(status) && "RUNNING".equals(currentStatus)) {
            // 분석 중지
            analysisStompHandler.stopAnalysis(cardId, card.getExchange(), card.getCurrencyPair());
            log.info("Analysis stopped for card: {}", cardId);
        }
        
        // 상태 업데이트
        cardStatusMap.put(cardId, status);
        cardTimestampMap.put(cardId, LocalDateTime.now().toString());
        card.setAction(status);
        
        return card;
    }
    
    /**
     * 카드 조회 및 웹소켓 연결 정보 포함
     *
     * @param cardId 카드 ID
     * @return 카드 정보와 웹소켓 정보를 포함한 맵 (없으면 null)
     */
    public Map<String, Object> getCardWithWebSocketInfo(String cardId) {
        AnalysisRequest card = getCard(cardId);
        if (card == null) {
            return null;
        }
        
        // 카드 정보 + 웹소켓 정보를 Map으로 반환
        Map<String, Object> result = new HashMap<>();
        result.put("card", card);
        result.put("websocket", createWebSocketInfo(cardId));
        
        return result;
    }
    
    /**
     * 웹소켓 연결 정보 생성
     *
     * @param cardId 카드 ID
     * @return 웹소켓 연결 정보
     */
    private Map<String, String> createWebSocketInfo(String cardId) {
        Map<String, String> wsInfo = new HashMap<>();
        wsInfo.put("endpoint", "/ws");
        wsInfo.put("topic", "/topic/analysis." + cardId);
        wsInfo.put("errorTopic", "/topic/analysis.error." + cardId);
        wsInfo.put("stopTopic", "/topic/analysis.stop." + cardId);
        return wsInfo;
    }
    
    /**
     * 모든 카드 조회
     *
     * @return 카드 목록
     */
    public List<AnalysisRequest> getAllCards() {
        List<AnalysisRequest> cards = new ArrayList<>(cardStore.values());
        
        // 상태와 가격 정보 추가
        for (AnalysisRequest card : cards) {
            String cardId = card.getCardId();
            card.setAction(cardStatusMap.getOrDefault(cardId, "UNKNOWN"));
            
            Double price = cardPriceMap.get(cardId);
            if (price != null) {
                // 메타 정보로 가격을 추가할 수 있음 (필요한 경우)
            }
        }
        
        return cards;
    }
    
    /**
     * 특정 카드 조회
     *
     * @param cardId 카드 ID
     * @return 카드 요청 객체 (없으면 null)
     */
    public AnalysisRequest getCard(String cardId) {
        AnalysisRequest request = cardStore.get(cardId);
        if (request != null) {
            // 상태 정보 추가
            request.setAction(cardStatusMap.getOrDefault(cardId, "UNKNOWN"));
        }
        return request;
    }
    
    /**
     * 카드 삭제 (분석 중지 포함)
     *
     * @param cardId 카드 ID
     * @return 성공 여부
     */
    public boolean deleteCard(String cardId) {
        AnalysisRequest card = cardStore.get(cardId);
        if (card == null) {
            return false;
        }
        
        // 실행 중인 분석 중지
        if ("RUNNING".equals(cardStatusMap.get(cardId))) {
            try {
                // 분석 중지
                analysisStompHandler.stopAnalysis(cardId, card.getExchange(), card.getCurrencyPair());
            } catch (Exception e) {
                log.warn("Error stopping analysis for cardId {}: {}", cardId, e.getMessage());
            }
        }
        
        // 카드 삭제
        cardStore.remove(cardId);
        cardStatusMap.remove(cardId);
        cardPriceMap.remove(cardId);
        cardTimestampMap.remove(cardId);
        
        log.info("Card deleted: {}", cardId);
        
        return true;
    }
    
    /**
     * 카드 ID 생성
     * 
     * @param exchange 거래소
     * @param currencyPair 거래쌍
     * @return 생성된 카드 ID
     */
    private String generateCardId(String exchange, String currencyPair) {
        String baseId = (exchange + "-" + currencyPair).toLowerCase();
        String random = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
        return baseId + "-" + random;
    }
}
