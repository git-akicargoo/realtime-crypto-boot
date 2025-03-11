package com.example.boot.exchange.layer7_trading.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 결과 엔티티
 */
@Entity
@Table(name = "simul_trading_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulTradingResult {
    
    /**
     * 모의거래 세션 ID
     */
    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;
    
    /**
     * 카드 ID
     */
    @Column(name = "card_id", length = 36, nullable = false)
    private String cardId;
    
    /**
     * 거래소
     */
    @Column(name = "exchange", length = 50, nullable = false)
    private String exchange;
    
    /**
     * 심볼
     */
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;
    
    /**
     * 기준 통화
     */
    @Column(name = "quote_currency", length = 10, nullable = false)
    private String quoteCurrency;
    
    /**
     * 통화 쌍
     */
    @Column(name = "currency_pair", length = 20, nullable = false)
    private String currencyPair;
    
    /**
     * 초기 잔액
     */
    @Column(name = "initial_balance", precision = 20, scale = 2, nullable = false)
    private BigDecimal initialBalance;
    
    /**
     * 최종 잔액
     */
    @Column(name = "final_balance", precision = 20, scale = 2, nullable = false)
    private BigDecimal finalBalance;
    
    /**
     * 수익률
     */
    @Column(name = "profit_percent", precision = 10, nullable = false)
    private Double profitPercent;
    
    /**
     * 매수 신호 기준값
     */
    @Column(name = "signal_threshold", precision = 5, nullable = false)
    private Double signalThreshold;
    
    /**
     * 익절 기준
     */
    @Column(name = "take_profit_percent", precision = 5, nullable = false)
    private Double takeProfitPercent;
    
    /**
     * 손절 기준
     */
    @Column(name = "stop_loss_percent", precision = 5, nullable = false)
    private Double stopLossPercent;
    
    /**
     * 총 거래 횟수
     */
    @Column(name = "total_trades", nullable = false)
    private Integer totalTrades;
    
    /**
     * 승리 거래 횟수
     */
    @Column(name = "win_trades", nullable = false)
    private Integer winTrades;
    
    /**
     * 패배 거래 횟수
     */
    @Column(name = "loss_trades", nullable = false)
    private Integer lossTrades;
    
    /**
     * 승률
     */
    @Column(name = "win_rate", precision = 5, nullable = false)
    private Double winRate;
    
    /**
     * 평균 수익률
     */
    @Column(name = "average_profit_percent", precision = 10, nullable = false)
    private Double averageProfitPercent;
    
    /**
     * 최대 수익률
     */
    @Column(name = "max_profit_percent", precision = 10, nullable = false)
    private Double maxProfitPercent;
    
    /**
     * 최대 손실률
     */
    @Column(name = "max_loss_percent", precision = 10, nullable = false)
    private Double maxLossPercent;
    
    /**
     * 시작 시간
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    /**
     * 종료 시간
     */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
    
    /**
     * 생성 시간
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
} 