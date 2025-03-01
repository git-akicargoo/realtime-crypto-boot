package com.example.boot.exchange.layer6_analysis.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    private String exchange;
    private String currencyPair;
    private String symbol;         // 코인 심볼 추가
    private String quoteCurrency;  // 화폐 추가
    private LocalDateTime analysisTime;
    private double currentPrice;
    private double priceChangePercent;
    private double volumeChangePercent;
    private double reboundProbability;
    private String analysisResult;  // "STRONG_REBOUND", "POSSIBLE_REBOUND", "NO_REBOUND"
    private String message;
    private double sma1Difference;  // 1분 SMA와의 차이(%)
    private double sma3Difference;  // 3분 SMA와의 차이(%)
    private boolean smaBreakout;    // SMA 돌파 여부
} 