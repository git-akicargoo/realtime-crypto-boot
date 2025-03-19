package com.example.boot.exchange.layer6_analysis.session_analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.CryptoAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 분석 관리자
 * 카드 ID 기반 분석 요청 관리 및 세션 연결 해제 시 자원 정리를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisManager {
    private final CryptoAnalysisService analysisService;
    private final AnalysisSessionRegistry sessionRegistry;
    
    // 카드 ID -> 분석 요청 매핑
    private final Map<String, AnalysisRequest> cardToRequestMap = new ConcurrentHashMap<>();
    
    // 카드 ID -> 세션 ID 매핑
    private final Map<String, String> cardToSessionMap = new ConcurrentHashMap<>();
    
    // 세션 ID -> 카드 ID 세트 매핑
    private final Map<String, Set<String>> sessionToCardsMap = new ConcurrentHashMap<>();
    
    /**
     * 분석 요청 등록
     */
    public void registerAnalysis(String cardId, AnalysisRequest request, String sessionId) {
        // 요청 및 세션 연결 저장
        cardToRequestMap.put(cardId, request);
        cardToSessionMap.put(cardId, sessionId);
        
        // 세션에 카드 ID 추가
        sessionToCardsMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(cardId);
        
        log.info("Registered analysis: cardId={}, sessionId={}, exchange={}, currencyPair={}", 
                cardId, sessionId, request.getExchange(), request.getCurrencyPair());
    }
    
    /**
     * 분석 요청 제거
     */
    public void unregisterAnalysis(String cardId) {
        // 세션에서 카드 ID 제거
        String sessionId = cardToSessionMap.remove(cardId);
        if (sessionId != null) {
            Set<String> cardIds = sessionToCardsMap.get(sessionId);
            if (cardIds != null) {
                cardIds.remove(cardId);
                if (cardIds.isEmpty()) {
                    sessionToCardsMap.remove(sessionId);
                }
            }
        }
        
        // 요청 맵에서 제거
        AnalysisRequest request = cardToRequestMap.remove(cardId);
        if (request != null) {
            // 실행 중인 분석 중지
            analysisService.stopAnalysis(request);
            log.info("Unregistered analysis: cardId={}, exchange={}, currencyPair={}", 
                    cardId, request.getExchange(), request.getCurrencyPair());
        }
    }
    
    /**
     * 세션 연결 해제 처리
     */
    public void handleSessionDisconnect(String sessionId) {
        Set<String> cardIds = sessionToCardsMap.remove(sessionId);
        if (cardIds != null && !cardIds.isEmpty()) {
            log.info("Session disconnected: {}. Stopping {} analysis tasks", sessionId, cardIds.size());
            new HashSet<>(cardIds).forEach(this::unregisterAnalysis);
        }
    }
    
    /**
     * 세션 유효성 검증
     * 세션이 유효하지 않은 분석 작업을 정리
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void cleanupInvalidSessions() {
        List<String> invalidCards = new ArrayList<>();
        
        // 유효하지 않은 세션에 연결된 카드 ID 찾기
        for (Map.Entry<String, String> entry : cardToSessionMap.entrySet()) {
            String cardId = entry.getKey();
            String sessionId = entry.getValue();
            
            if (!sessionRegistry.isSessionActive(sessionId)) {
                invalidCards.add(cardId);
            }
        }
        
        // 유효하지 않은 카드 정리
        if (!invalidCards.isEmpty()) {
            log.info("Cleaning up {} analysis tasks with invalid sessions", invalidCards.size());
            invalidCards.forEach(this::unregisterAnalysis);
        }
    }
    
    /**
     * 분석 처리 전 세션 유효성 확인
     */
    public boolean isValidAnalysis(String cardId) {
        String sessionId = cardToSessionMap.get(cardId);
        return sessionId != null && sessionRegistry.isSessionActive(sessionId);
    }
    
    /**
     * 카드 ID로 분석 요청 가져오기
     */
    public AnalysisRequest getAnalysisRequest(String cardId) {
        return cardToRequestMap.get(cardId);
    }
    
    /**
     * 활성 분석 수 반환
     */
    public int getActiveAnalysisCount() {
        return cardToRequestMap.size();
    }
} 