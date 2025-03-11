package com.example.boot.exchange.layer7_trading.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 시작 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulTradingRequest {
    private String cardId;                   // 분석 카드 ID
    private String exchange;                 // 거래소
    private String symbol;                   // 코인 심볼
    private String quoteCurrency;            // 기준 화폐
    private String currencyPair;             // 거래쌍
    
    private BigDecimal initialBalance;       // 초기 자금
    private double signalThreshold;          // 매수 신호 기준값 (0-100%)
    private double takeProfitPercent;        // 익절 기준 (%)
    private double stopLossPercent;          // 손절 기준 (%)
    
    private long timestamp;                  // 요청 시간 (밀리초)
} 