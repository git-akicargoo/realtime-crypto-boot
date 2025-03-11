package com.example.boot.exchange.layer7_trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.boot.exchange.layer7_trading.model.TradeHistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulTradingResponse {
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
    
    private int totalTrades;                 // 총 거래 횟수
    private int winTrades;                   // 이익 거래 횟수
    private int lossTrades;                  // 손실 거래 횟수
    
    private LocalDateTime startTime;         // 시작 시간
    private LocalDateTime lastUpdateTime;    // 마지막 업데이트 시간
    
    private List<TradeHistory> recentTrades; // 최근 거래 내역 (최대 10개)
    
    private String message;                  // 메시지
    
    // 추가: 최종 결과 요약 정보
    private int completedTrades;             // 완료된 거래 건수
    private double winRate;                  // 승률 (%)
    private double averageProfitPercent;     // 평균 수익률 (%)
    private double maxProfitPercent;         // 최대 수익률 (%)
    private double maxLossPercent;           // 최대 손실률 (%)
    private boolean completed;               // 모의거래 완료 여부
} 