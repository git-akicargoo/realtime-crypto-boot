package com.example.boot.exchange.layer6_analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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