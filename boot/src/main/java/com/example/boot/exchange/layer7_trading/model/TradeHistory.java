package com.example.boot.exchange.layer7_trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 내역 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeHistory {
    private String tradeId;              // 거래 ID
    private String sessionId;            // 모의거래 세션 ID
    private String cardId;               // 분석 카드 ID
    
    private String type;                 // 거래 유형 (BUY, SELL)
    private BigDecimal price;            // 거래 가격
    private BigDecimal amount;           // 거래 수량
    private BigDecimal total;            // 거래 총액
    
    private BigDecimal balanceBefore;    // 거래 전 잔액
    private BigDecimal balanceAfter;     // 거래 후 잔액
    private double profitPercent;        // 수익률 (%)
    
    private String reason;               // 거래 이유 (SIGNAL, TAKE_PROFIT, STOP_LOSS)
    private double signalStrength;       // 신호 강도 (매수 시)
    
    private LocalDateTime tradeTime;     // 거래 시간
    private String status;               // 상태 (COMPLETED, CANCELED)
} 