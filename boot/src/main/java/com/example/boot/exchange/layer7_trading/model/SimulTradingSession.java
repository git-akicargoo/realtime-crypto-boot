package com.example.boot.exchange.layer7_trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 세션 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulTradingSession {
    private String sessionId;                // 모의거래 세션 ID
    private String cardId;                   // 분석 카드 ID
    private String exchange;                 // 거래소
    private String symbol;                   // 코인 심볼
    private String quoteCurrency;            // 기준 화폐
    private String currencyPair;             // 거래쌍
    
    private BigDecimal initialBalance;       // 초기 자금
    private BigDecimal currentBalance;       // 현재 자금
    private double profitPercent;            // 수익률 (%)
    
    private double signalThreshold;          // 매수 신호 기준값 (0-100%)
    private double takeProfitPercent;        // 익절 기준 (%)
    private double stopLossPercent;          // 손절 기준 (%)
    
    private String status;                   // 상태 (RUNNING, STOPPED, COMPLETED)
    private boolean holdingPosition;         // 포지션 보유 여부
    private BigDecimal entryPrice;           // 진입 가격
    private LocalDateTime entryTime;         // 진입 시간
    private BigDecimal positionSize;         // 포지션 크기
    
    private int totalTrades;                 // 총 거래 횟수
    private int winTrades;                   // 이익 거래 횟수
    private int lossTrades;                  // 손실 거래 횟수
    
    private long startTime;                  // 시작 시간 (밀리초)
    private long lastUpdateTime;             // 마지막 업데이트 시간 (밀리초)
    
    @Builder.Default
    private List<TradeHistory> tradeHistory = new ArrayList<>(); // 거래 내역
    
    // 추가: 최대 거래 건수 (기본값: 10)
    @Builder.Default
    private int maxTrades = 10;
    
    // 추가: 다음 거래 대기 상태 (매수 후 매도까지 대기)
    @Builder.Default
    private boolean waitingForNextTrade = false;
    
    // 추가: 거래당 금액 (기본값: 10만원)
    @Builder.Default
    private BigDecimal tradeAmount = new BigDecimal("100000");
} 