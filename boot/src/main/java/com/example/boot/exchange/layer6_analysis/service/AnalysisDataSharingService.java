package com.example.boot.exchange.layer6_analysis.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.dto.CardInfoDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * 분석 데이터 공유 서비스
 * 분석 웹소켓과 모의 거래 기능 간의 데이터 공유를 담당
 */
@Slf4j
@Service
public class AnalysisDataSharingService {
    
    // 카드 ID를 키로 하는 분석 요청 정보 저장소
    private final Map<String, AnalysisRequest> analysisRequests = new ConcurrentHashMap<>();
    
    // 카드 ID를 키로 하는 최신 분석 결과 저장소
    private final Map<String, AnalysisResponse> latestAnalysisResults = new ConcurrentHashMap<>();
    
    // 카드 ID를 키로 하는 카드 정보 저장소
    private final Map<String, CardInfoDTO> cardInfoMap = new ConcurrentHashMap<>();
    
    // 카드 ID를 키로 하는 구독자 목록 저장소
    private final Map<String, List<Consumer<AnalysisResponse>>> subscribers = new ConcurrentHashMap<>();
    
    /**
     * 분석 요청 정보 저장
     * @param request 분석 요청 정보
     */
    public void saveAnalysisRequest(AnalysisRequest request) {
        String cardId = request.getCardId();
        if (cardId == null || cardId.isEmpty()) {
            log.warn("카드 ID가 없는 분석 요청은 저장할 수 없습니다.");
            return;
        }
        
        log.info("분석 요청 정보 저장: {}", cardId);
        analysisRequests.put(cardId, request);
        
        // 카드 정보 생성 및 저장
        CardInfoDTO cardInfo = CardInfoDTO.builder()
                .cardId(cardId)
                .exchange(request.getExchange())
                .symbol(request.getSymbol())
                .quoteCurrency(request.getQuoteCurrency())
                .currencyPair(request.getCurrencyPair())
                .tradingStyle(request.getTradingStyle())
                .createdAt(LocalDateTime.now())
                .timestamp(request.getTimestamp())
                .build();
        
        cardInfoMap.put(cardId, cardInfo);
    }
    
    /**
     * 분석 데이터 업데이트
     * @param cardId 카드 ID
     * @param response 분석 응답 데이터
     */
    public void updateAnalysisData(String cardId, AnalysisResponse response) {
        if (cardId == null || cardId.isEmpty()) {
            log.warn("카드 ID가 없는 분석 결과는 업데이트할 수 없습니다.");
            return;
        }
        
        log.debug("분석 데이터 업데이트: {}", cardId);
        latestAnalysisResults.put(cardId, response);
        
        // 카드 정보 업데이트
        CardInfoDTO cardInfo = cardInfoMap.get(cardId);
        if (cardInfo != null) {
            cardInfo.setCurrentPrice(response.getCurrentPrice());
            cardInfo.setBuySignalStrength(response.getBuySignalStrength());
            cardInfo.setReboundProbability(response.getReboundProbability());
            cardInfo.setMarketCondition(response.getMarketCondition());
            
            cardInfoMap.put(cardId, cardInfo);
        }
        
        // 구독자들에게 알림
        notifySubscribers(cardId, response);
    }
    
    /**
     * 구독자들에게 알림
     * @param cardId 카드 ID
     * @param response 분석 응답 데이터
     */
    private void notifySubscribers(String cardId, AnalysisResponse response) {
        List<Consumer<AnalysisResponse>> cardSubscribers = subscribers.get(cardId);
        if (cardSubscribers != null && !cardSubscribers.isEmpty()) {
            for (Consumer<AnalysisResponse> subscriber : cardSubscribers) {
                try {
                    subscriber.accept(response);
                } catch (Exception e) {
                    log.error("구독자에게 알림 중 오류 발생: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * 카드 제거
     * @param cardId 카드 ID
     */
    public void removeCard(String cardId) {
        if (cardId == null || cardId.isEmpty()) {
            return;
        }
        
        log.info("카드 제거: {}", cardId);
        analysisRequests.remove(cardId);
        latestAnalysisResults.remove(cardId);
        cardInfoMap.remove(cardId);
        subscribers.remove(cardId);
    }
    
    /**
     * 모든 카드 정보 목록 조회
     * @return 카드 정보 목록
     */
    public List<CardInfoDTO> getAllCardInfoList() {
        return new ArrayList<>(cardInfoMap.values());
    }
    
    /**
     * 특정 카드의 최신 분석 결과 조회
     * @param cardId 카드 ID
     * @return 최신 분석 결과
     */
    public AnalysisResponse getLatestAnalysisResult(String cardId) {
        return latestAnalysisResults.get(cardId);
    }
    
    /**
     * 특정 카드의 분석 요청 정보 조회
     * @param cardId 카드 ID
     * @return 분석 요청 정보
     */
    public AnalysisRequest getAnalysisRequest(String cardId) {
        return analysisRequests.get(cardId);
    }
    
    /**
     * 특정 카드의 정보 조회
     * @param cardId 카드 ID
     * @return 카드 정보
     */
    public CardInfoDTO getCardInfo(String cardId) {
        return cardInfoMap.get(cardId);
    }
    
    /**
     * 분석 데이터 구독
     * @param cardId 카드 ID
     * @param subscriber 구독자
     */
    public void subscribeToAnalysisData(String cardId, Consumer<AnalysisResponse> subscriber) {
        if (cardId == null || cardId.isEmpty() || subscriber == null) {
            return;
        }
        
        log.info("분석 데이터 구독 추가: {}", cardId);
        subscribers.computeIfAbsent(cardId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        
        // 최신 분석 결과가 있으면 즉시 전달
        AnalysisResponse latestResult = latestAnalysisResults.get(cardId);
        if (latestResult != null) {
            try {
                subscriber.accept(latestResult);
            } catch (Exception e) {
                log.error("구독자에게 최신 분석 결과 전달 중 오류 발생: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 분석 데이터 구독 해제
     * @param cardId 카드 ID
     * @param subscriber 구독자
     */
    public void unsubscribeFromAnalysisData(String cardId, Consumer<AnalysisResponse> subscriber) {
        if (cardId == null || cardId.isEmpty() || subscriber == null) {
            return;
        }
        
        log.info("분석 데이터 구독 해제: {}", cardId);
        List<Consumer<AnalysisResponse>> cardSubscribers = subscribers.get(cardId);
        if (cardSubscribers != null) {
            cardSubscribers.remove(subscriber);
            
            // 구독자가 없으면 목록 제거
            if (cardSubscribers.isEmpty()) {
                subscribers.remove(cardId);
            }
        }
    }
} 