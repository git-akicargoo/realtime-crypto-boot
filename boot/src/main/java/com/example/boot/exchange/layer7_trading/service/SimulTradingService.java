package com.example.boot.exchange.layer7_trading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.AnalysisDataSharingService;
import com.example.boot.exchange.layer7_trading.dto.SimulTradingRequest;
import com.example.boot.exchange.layer7_trading.dto.SimulTradingResponse;
import com.example.boot.exchange.layer7_trading.entity.SimulTradingResult;
import com.example.boot.exchange.layer7_trading.event.SimulTradingUpdateEvent;
import com.example.boot.exchange.layer7_trading.model.SimulTradingSession;
import com.example.boot.exchange.layer7_trading.model.TradeHistory;
import com.example.boot.exchange.layer7_trading.repository.SimulTradingResultRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의거래 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulTradingService {
    
    private final AnalysisDataSharingService dataSharingService;
    private final SimulTradingResultRepository simulTradingResultRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, SimulTradingSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AnalysisResponse>> analysisSubscribers = new ConcurrentHashMap<>();
    
    /**
     * 모의거래 시작
     * @param request 모의거래 요청 정보
     * @return 모의거래 응답 정보
     */
    public SimulTradingResponse startSimulTrading(SimulTradingRequest request) {
        String cardId = request.getCardId();
        
        // 카드 ID 유효성 검사
        if (cardId == null || cardId.isEmpty()) {
            throw new IllegalArgumentException("카드 ID가 유효하지 않습니다.");
        }
        
        // 카드 존재 여부 확인
        AnalysisResponse latestAnalysis = dataSharingService.getLatestAnalysisResult(cardId);
        if (latestAnalysis == null) {
            throw new IllegalArgumentException("선택한 카드의 분석 결과가 없습니다.");
        }
        
        // 세션 ID 생성
        String sessionId = UUID.randomUUID().toString();
        
        // 초기 잔액 설정 (기본값: 1,000,000)
        BigDecimal initialBalance = request.getInitialBalance();
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            initialBalance = new BigDecimal("1000000");
        }
        
        // 모의거래 세션 생성
        SimulTradingSession session = SimulTradingSession.builder()
                .sessionId(sessionId)
                .cardId(cardId)
                .exchange(latestAnalysis.getExchange())
                .symbol(latestAnalysis.getSymbol())
                .quoteCurrency(latestAnalysis.getQuoteCurrency())
                .currencyPair(latestAnalysis.getCurrencyPair())
                .initialBalance(initialBalance)
                .currentBalance(initialBalance)
                .signalThreshold(request.getSignalThreshold())
                .takeProfitPercent(request.getTakeProfitPercent())
                .stopLossPercent(request.getStopLossPercent())
                .status("RUNNING")
                .holdingPosition(false)
                .totalTrades(0)
                .winTrades(0)
                .lossTrades(0)
                .startTime(System.currentTimeMillis())
                .lastUpdateTime(System.currentTimeMillis())
                .maxTrades(10) // 최대 거래 건수 설정
                .waitingForNextTrade(false) // 다음 거래 대기 상태 초기화
                .tradeAmount(new BigDecimal("100000")) // 거래당 금액 설정
                .build();
        
        // 세션 저장
        activeSessions.put(sessionId, session);
        
        // 분석 데이터 구독
        Consumer<AnalysisResponse> subscriber = analysisData -> processAnalysisUpdate(session, analysisData);
        analysisSubscribers.put(sessionId, subscriber);
        dataSharingService.subscribeToAnalysisData(cardId, subscriber);
        
        // 응답 생성
        return createResponse(session);
    }
    
    /**
     * 모의거래 중지
     * @param sessionId 모의거래 세션 ID
     * @return 모의거래 응답 정보
     */
    public SimulTradingResponse stopSimulTrading(String sessionId) {
        // 세션 존재 여부 확인
        SimulTradingSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("존재하지 않는 모의거래 세션입니다.");
        }
        
        // 포지션 보유 중이면 청산
        if (session.isHoldingPosition()) {
            // 최신 분석 데이터 가져오기
            AnalysisResponse latestAnalysis = dataSharingService.getLatestAnalysisResult(session.getCardId());
            if (latestAnalysis != null) {
                // 청산 처리
                executeSell(session, new BigDecimal(latestAnalysis.getCurrentPrice()), "SESSION_CLOSE");
            }
        }
        
        // 세션 상태 업데이트
        session.setStatus("STOPPED");
        session.setLastUpdateTime(System.currentTimeMillis());
        
        // 구독 해제
        Consumer<AnalysisResponse> subscriber = analysisSubscribers.remove(sessionId);
        if (subscriber != null) {
            dataSharingService.unsubscribeFromAnalysisData(session.getCardId(), subscriber);
        }
        
        // 모의거래 결과 저장
        saveSimulTradingResult(session);
        
        // 응답 생성
        SimulTradingResponse response = createResponse(session);
        
        // 세션 제거
        activeSessions.remove(sessionId);
        
        return response;
    }
    
    /**
     * 모의거래 상태 조회
     * @param sessionId 모의거래 세션 ID
     * @return 모의거래 응답 정보
     */
    public SimulTradingResponse getSimulTradingStatus(String sessionId) {
        // 세션 존재 여부 확인
        SimulTradingSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("존재하지 않는 모의거래 세션입니다.");
        }
        
        // 응답 생성
        return createResponse(session);
    }
    
    /**
     * 분석 데이터 업데이트 처리
     * @param session 모의거래 세션
     * @param analysisData 분석 데이터
     */
    private void processAnalysisUpdate(SimulTradingSession session, AnalysisResponse analysisData) {
        // 세션이 중지 상태면 처리하지 않음
        if (!"RUNNING".equals(session.getStatus())) {
            return;
        }
        
        // 현재 가격
        BigDecimal currentPrice = new BigDecimal(analysisData.getCurrentPrice());
        
        // 포지션 보유 중인 경우
        if (session.isHoldingPosition()) {
            // 익절/손절 확인
            checkTakeProfitStopLoss(session, currentPrice);
        } else {
            // 매수 신호 확인
            checkBuySignal(session, analysisData);
        }
        
        // 세션 업데이트 시간 갱신
        session.setLastUpdateTime(System.currentTimeMillis());
        
        // 상태 업데이트 이벤트 발행
        publishUpdateEvent(session.getSessionId(), createResponse(session));
    }
    
    /**
     * 매수 신호 확인
     * @param session 모의거래 세션
     * @param analysisData 분석 데이터
     */
    private void checkBuySignal(SimulTradingSession session, AnalysisResponse analysisData) {
        // 이미 최대 거래 건수에 도달했거나 다음 거래 대기 중이면 처리하지 않음
        if (session.getTotalTrades() >= session.getMaxTrades() || session.isWaitingForNextTrade()) {
            return;
        }
        
        // 매수 신호 강도
        double buySignalStrength = analysisData.getBuySignalStrength();
        
        // 매수 신호 기준값 이상이면 매수
        if (buySignalStrength >= session.getSignalThreshold()) {
            // 현재 가격
            BigDecimal currentPrice = new BigDecimal(analysisData.getCurrentPrice());
            
            // 매수 처리
            executeBuy(session, currentPrice, buySignalStrength);
            
            // 다음 거래 대기 상태로 설정
            session.setWaitingForNextTrade(true);
            
            // 상태 업데이트 이벤트 발행
            publishUpdateEvent(session.getSessionId(), createResponse(session));
        }
    }
    
    /**
     * 익절/손절 확인
     * @param session 모의거래 세션
     * @param currentPrice 현재 가격
     */
    private void checkTakeProfitStopLoss(SimulTradingSession session, BigDecimal currentPrice) {
        // 진입 가격
        BigDecimal entryPrice = session.getEntryPrice();
        
        // 가격 변화율 계산
        BigDecimal priceChangePercent = currentPrice.subtract(entryPrice)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        // 익절 조건 확인
        if (priceChangePercent.doubleValue() >= session.getTakeProfitPercent()) {
            // 익절 처리
            executeSell(session, currentPrice, "TAKE_PROFIT");
            return;
        }
        
        // 손절 조건 확인
        if (priceChangePercent.doubleValue() <= -session.getStopLossPercent()) {
            // 손절 처리
            executeSell(session, currentPrice, "STOP_LOSS");
        }
    }
    
    /**
     * 매수 처리
     * @param session 모의거래 세션
     * @param price 매수 가격
     * @param signalStrength 신호 강도
     */
    private void executeBuy(SimulTradingSession session, BigDecimal price, double signalStrength) {
        // 이미 포지션 보유 중이면 처리하지 않음
        if (session.isHoldingPosition()) {
            return;
        }
        
        // 매수 금액 (10만원 고정)
        BigDecimal buyAmount = session.getTradeAmount();
        
        // 현재 잔액이 매수 금액보다 작으면 처리하지 않음
        if (session.getCurrentBalance().compareTo(buyAmount) < 0) {
            log.warn("잔액 부족: 필요={}, 현재={}", buyAmount, session.getCurrentBalance());
            return;
        }
        
        // 매수 수량
        BigDecimal quantity = buyAmount.divide(price, 8, RoundingMode.HALF_UP);
        
        // 거래 내역 생성
        TradeHistory trade = TradeHistory.builder()
                .tradeId(UUID.randomUUID().toString())
                .sessionId(session.getSessionId())
                .cardId(session.getCardId())
                .type("BUY")
                .price(price)
                .amount(quantity)
                .total(buyAmount)
                .balanceBefore(session.getCurrentBalance())
                .balanceAfter(session.getCurrentBalance().subtract(buyAmount))
                .reason("SIGNAL")
                .signalStrength(signalStrength)
                .tradeTime(LocalDateTime.now())
                .status("COMPLETED")
                .build();
        
        // 세션 업데이트
        session.setHoldingPosition(true);
        session.setEntryPrice(price);
        session.setEntryTime(LocalDateTime.now());
        session.setPositionSize(quantity);
        session.setCurrentBalance(session.getCurrentBalance().subtract(buyAmount));
        session.setTotalTrades(session.getTotalTrades() + 1);
        
        // 거래 내역 추가
        session.getTradeHistory().add(trade);
        
        log.info("매수 처리: 세션={}, 가격={}, 수량={}, 총액={}", 
                session.getSessionId(), price, quantity, buyAmount);
    }
    
    /**
     * 매도 처리
     * @param session 모의거래 세션
     * @param price 매도 가격
     * @param reason 매도 이유
     */
    private void executeSell(SimulTradingSession session, BigDecimal price, String reason) {
        // 포지션 보유 중이 아니면 처리하지 않음
        if (!session.isHoldingPosition()) {
            return;
        }
        
        // 매도 수량
        BigDecimal quantity = session.getPositionSize();
        
        // 매도 금액
        BigDecimal sellAmount = price.multiply(quantity);
        
        // 손익 계산
        BigDecimal entryAmount = session.getEntryPrice().multiply(quantity);
        BigDecimal profit = sellAmount.subtract(entryAmount);
        double profitPercent = profit.divide(entryAmount, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();
        
        // 거래 내역 생성
        TradeHistory trade = TradeHistory.builder()
                .tradeId(UUID.randomUUID().toString())
                .sessionId(session.getSessionId())
                .cardId(session.getCardId())
                .type("SELL")
                .price(price)
                .amount(quantity)
                .total(sellAmount)
                .balanceBefore(session.getCurrentBalance())
                .balanceAfter(session.getCurrentBalance().add(sellAmount))
                .profitPercent(profitPercent)
                .reason(reason)
                .tradeTime(LocalDateTime.now())
                .status("COMPLETED")
                .build();
        
        // 세션 업데이트
        session.setHoldingPosition(false);
        session.setEntryPrice(null);
        session.setEntryTime(null);
        session.setPositionSize(null);
        session.setCurrentBalance(session.getCurrentBalance().add(sellAmount));
        
        // 수익률 업데이트
        BigDecimal totalProfitPercent = session.getCurrentBalance().subtract(session.getInitialBalance())
                .divide(session.getInitialBalance(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        session.setProfitPercent(totalProfitPercent.doubleValue());
        
        // 승/패 카운트 업데이트
        if (profit.compareTo(BigDecimal.ZERO) > 0) {
            session.setWinTrades(session.getWinTrades() + 1);
        } else {
            session.setLossTrades(session.getLossTrades() + 1);
        }
        
        // 거래 내역 추가
        session.getTradeHistory().add(trade);
        
        log.info("매도 처리: 세션={}, 가격={}, 수량={}, 총액={}, 수익률={}%, 이유={}", 
                session.getSessionId(), price, quantity, sellAmount, profitPercent, reason);
        
        // 다음 거래 대기 상태 해제
        session.setWaitingForNextTrade(false);
        
        // 모든 거래가 완료되었는지 확인
        if (session.getTotalTrades() >= session.getMaxTrades()) {
            // 모의거래 완료 처리
            completeSimulTrading(session);
        }
        
        // 상태 업데이트 이벤트 발행
        publishUpdateEvent(session.getSessionId(), createResponse(session));
    }
    
    /**
     * 모의거래 완료 처리
     * @param session 모의거래 세션
     */
    private void completeSimulTrading(SimulTradingSession session) {
        log.info("모의거래 완료: 세션={}, 총 거래 횟수={}, 승={}, 패={}, 수익률={}%", 
                session.getSessionId(), session.getTotalTrades(), session.getWinTrades(), 
                session.getLossTrades(), session.getProfitPercent());
        
        // 세션 상태 업데이트
        session.setStatus("COMPLETED");
        session.setLastUpdateTime(System.currentTimeMillis());
        
        // 구독 해제
        Consumer<AnalysisResponse> subscriber = analysisSubscribers.remove(session.getSessionId());
        if (subscriber != null) {
            dataSharingService.unsubscribeFromAnalysisData(session.getCardId(), subscriber);
        }
        
        // 모의거래 결과 저장
        saveSimulTradingResult(session);
        
        // 최종 결과 이벤트 발행
        SimulTradingResponse response = createResponse(session);
        publishUpdateEvent(session.getSessionId(), response);
    }
    
    /**
     * 모의거래 결과 저장
     * @param session 모의거래 세션
     */
    private void saveSimulTradingResult(SimulTradingSession session) {
        try {
            // 승률 계산
            double winRate = 0;
            if (session.getTotalTrades() > 0) {
                winRate = (double) session.getWinTrades() / session.getTotalTrades() * 100;
            }
            
            // 평균 수익률, 최대 수익률, 최대 손실률 계산
            double averageProfitPercent = 0;
            double maxProfitPercent = 0;
            double maxLossPercent = 0;
            
            List<Double> profitPercentList = new ArrayList<>();
            
            for (TradeHistory trade : session.getTradeHistory()) {
                if ("SELL".equals(trade.getType())) {
                    double profitPercent = trade.getProfitPercent();
                    profitPercentList.add(profitPercent);
                    
                    if (profitPercent > maxProfitPercent) {
                        maxProfitPercent = profitPercent;
                    }
                    
                    if (profitPercent < maxLossPercent) {
                        maxLossPercent = profitPercent;
                    }
                }
            }
            
            if (!profitPercentList.isEmpty()) {
                double totalProfitPercent = 0;
                for (Double percent : profitPercentList) {
                    totalProfitPercent += percent;
                }
                averageProfitPercent = totalProfitPercent / profitPercentList.size();
            }
            
            // 모의거래 결과 엔티티 생성
            SimulTradingResult result = SimulTradingResult.builder()
                    .sessionId(session.getSessionId())
                    .cardId(session.getCardId())
                    .exchange(session.getExchange())
                    .symbol(session.getSymbol())
                    .quoteCurrency(session.getQuoteCurrency())
                    .currencyPair(session.getCurrencyPair())
                    .initialBalance(session.getInitialBalance())
                    .finalBalance(session.getCurrentBalance())
                    .profitPercent(session.getProfitPercent())
                    .signalThreshold(session.getSignalThreshold())
                    .takeProfitPercent(session.getTakeProfitPercent())
                    .stopLossPercent(session.getStopLossPercent())
                    .totalTrades(session.getTotalTrades())
                    .winTrades(session.getWinTrades())
                    .lossTrades(session.getLossTrades())
                    .winRate(winRate)
                    .averageProfitPercent(averageProfitPercent)
                    .maxProfitPercent(maxProfitPercent)
                    .maxLossPercent(maxLossPercent)
                    .startTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getStartTime()), ZoneId.systemDefault()))
                    .endTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getLastUpdateTime()), ZoneId.systemDefault()))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // 저장
            simulTradingResultRepository.save(result);
            
            log.info("모의거래 결과 저장 완료: 세션={}", session.getSessionId());
        } catch (Exception e) {
            log.error("모의거래 결과 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 응답 생성
     * @param session 모의거래 세션
     * @return 모의거래 응답 정보
     */
    private SimulTradingResponse createResponse(SimulTradingSession session) {
        // 최근 거래 내역 (최대 10개)
        List<TradeHistory> recentTrades = new ArrayList<>();
        List<TradeHistory> allTrades = session.getTradeHistory();
        
        int startIndex = Math.max(0, allTrades.size() - 10);
        for (int i = startIndex; i < allTrades.size(); i++) {
            recentTrades.add(allTrades.get(i));
        }
        
        // 승률 계산
        double winRate = 0;
        if (session.getTotalTrades() > 0) {
            winRate = (double) session.getWinTrades() / session.getTotalTrades() * 100;
        }
        
        // 평균 수익률, 최대 수익률, 최대 손실률 계산
        double averageProfitPercent = 0;
        double maxProfitPercent = 0;
        double maxLossPercent = 0;
        
        List<Double> profitPercentList = new ArrayList<>();
        
        for (TradeHistory trade : allTrades) {
            if ("SELL".equals(trade.getType())) {
                double profitPercent = trade.getProfitPercent();
                profitPercentList.add(profitPercent);
                
                if (profitPercent > maxProfitPercent) {
                    maxProfitPercent = profitPercent;
                }
                
                if (profitPercent < maxLossPercent) {
                    maxLossPercent = profitPercent;
                }
            }
        }
        
        if (!profitPercentList.isEmpty()) {
            double totalProfitPercent = 0;
            for (Double percent : profitPercentList) {
                totalProfitPercent += percent;
            }
            averageProfitPercent = totalProfitPercent / profitPercentList.size();
        }
        
        // 응답 생성
        return SimulTradingResponse.builder()
                .sessionId(session.getSessionId())
                .cardId(session.getCardId())
                .exchange(session.getExchange())
                .symbol(session.getSymbol())
                .quoteCurrency(session.getQuoteCurrency())
                .currencyPair(session.getCurrencyPair())
                .initialBalance(session.getInitialBalance())
                .currentBalance(session.getCurrentBalance())
                .profitPercent(session.getProfitPercent())
                .signalThreshold(session.getSignalThreshold())
                .takeProfitPercent(session.getTakeProfitPercent())
                .stopLossPercent(session.getStopLossPercent())
                .status(session.getStatus())
                .holdingPosition(session.isHoldingPosition())
                .entryPrice(session.getEntryPrice())
                .entryTime(session.getEntryTime())
                .totalTrades(session.getTotalTrades())
                .winTrades(session.getWinTrades())
                .lossTrades(session.getLossTrades())
                .startTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getStartTime()), ZoneId.systemDefault()))
                .lastUpdateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getLastUpdateTime()), ZoneId.systemDefault()))
                .recentTrades(recentTrades)
                .completedTrades(session.getTotalTrades())
                .winRate(winRate)
                .averageProfitPercent(averageProfitPercent)
                .maxProfitPercent(maxProfitPercent)
                .maxLossPercent(maxLossPercent)
                .completed("COMPLETED".equals(session.getStatus()))
                .message("COMPLETED".equals(session.getStatus()) ? "모의거래가 완료되었습니다." : null)
                .build();
    }
    
    /**
     * 모의거래 상태 업데이트 이벤트 발행
     * @param sessionId 모의거래 세션 ID
     * @param response 모의거래 응답 정보
     */
    private void publishUpdateEvent(String sessionId, SimulTradingResponse response) {
        SimulTradingUpdateEvent event = new SimulTradingUpdateEvent(this, sessionId, response);
        eventPublisher.publishEvent(event);
    }
} 