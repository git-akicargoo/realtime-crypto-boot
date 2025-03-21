package com.example.boot.web.card.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.CryptoAnalysisService;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisManager;

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
    
    // 메모리 기반 카드 저장소 (카드 ID -> 분석 요청)
    private final Map<String, AnalysisRequest> cardStore = new ConcurrentHashMap<>();
    
    // 카드 상태 저장소
    private final Map<String, String> cardStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Double> cardPriceMap = new ConcurrentHashMap<>();
    private final Map<String, String> cardTimestampMap = new ConcurrentHashMap<>();
    
    /**
     * 카드 생성
     *
     * @param request 분석 요청 객체
     * @return 생성된 카드 요청 객체
     */
    public AnalysisRequest createCard(AnalysisRequest request) {
        log.info("Creating card for {} - {}", request.getExchange(), request.getCurrencyPair());
        
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
        cardStatusMap.put(cardId, "CREATED");
        cardTimestampMap.put(cardId, LocalDateTime.now().toString());
        
        return request;
    }
    
    /**
     * 카드 생성 및 분석 시작
     *
     * @param request 분석 요청 객체
     * @param sessionId 세션 ID
     * @return 생성된 카드 요청 객체
     */
    public AnalysisRequest createCardAndStartAnalysis(AnalysisRequest request, String sessionId) {
        // 카드 생성
        AnalysisRequest createdRequest = createCard(request);
        
        // 분석 시작
        startAnalysis(createdRequest, sessionId);
        
        return createdRequest;
    }
    
    /**
     * 분석 시작
     *
     * @param request 분석 요청 객체
     * @param sessionId 세션 ID
     */
    public void startAnalysis(AnalysisRequest request, String sessionId) {
        String cardId = request.getCardId();
        log.info("Starting analysis for cardId: {}, sessionId: {}", cardId, sessionId);
        
        // 분석 세션 등록
        analysisManager.registerAnalysis(cardId, request, sessionId);
        
        // 분석 시작
        analysisService.startAnalysis(request);
        
        // 카드 상태 업데이트
        updateCardStatus(cardId, "RUNNING", null);
    }
    
    /**
     * 분석 중지
     *
     * @param cardId 카드 ID
     */
    public void stopAnalysis(String cardId) {
        log.info("Stopping analysis for cardId: {}", cardId);
        
        // 카드 조회
        AnalysisRequest request = getCard(cardId);
        if (request == null) {
            log.warn("Card not found: {}", cardId);
            return;
        }
        
        // 분석 중지
        try {
            analysisService.stopAnalysis(request);
            analysisManager.unregisterAnalysis(cardId);
            
            // 카드 상태 업데이트
            updateCardStatus(cardId, "STOPPED", null);
        } catch (Exception e) {
            log.error("Error stopping analysis: {}", e.getMessage(), e);
            updateCardStatus(cardId, "ERROR", null);
        }
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
     * 카드 상태 업데이트
     *
     * @param cardId 카드 ID
     * @param status 상태
     * @param price  현재 가격
     * @return 성공 여부
     */
    public boolean updateCardStatus(String cardId, String status, Double price) {
        AnalysisRequest card = cardStore.get(cardId);
        if (card == null) {
            return false;
        }
        
        cardStatusMap.put(cardId, status);
        if (price != null) {
            cardPriceMap.put(cardId, price);
        }
        cardTimestampMap.put(cardId, LocalDateTime.now().toString());
        
        return true;
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
        try {
            stopAnalysis(cardId);
        } catch (Exception e) {
            log.warn("Error stopping analysis for cardId {}: {}", cardId, e.getMessage());
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
     */
    private String generateCardId(String exchange, String currencyPair) {
        String baseId = (exchange + "-" + currencyPair).toLowerCase();
        String random = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
        return baseId + "-" + random;
    }
}
