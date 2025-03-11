package com.example.boot.exchange.layer7_trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모의거래 테스트 요청 DTO (기존 코드 유지)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationTradeRequest {
    private TradingSettings tradingSettings;
    private MarketData marketData;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradingSettings {
        private String tradingStyle;
        private BigDecimal investmentAmount;
        private Double stopLossPercent;
        private Double takeProfitPercent;
        private LocalDateTime timestamp;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketData {
        private String exchange;
        private String symbol;
        private String quoteCurrency;
        private String currencyPair;
        private BigDecimal currentPrice;
        private Double buySignalStrength;
        private Double reboundProbability;
        private Double priceChangePercent;
        private Double volume;
        private LocalDateTime timestamp;
    }
} 