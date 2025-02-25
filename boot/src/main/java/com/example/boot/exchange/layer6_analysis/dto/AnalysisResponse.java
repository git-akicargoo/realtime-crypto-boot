package com.example.boot.exchange.layer6_analysis.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisResponse {
    private String exchange;
    private String currencyPair;
    private LocalDateTime analysisTime;
    private double currentPrice;
    private double priceChangePercent;
    private double volumeChangePercent;
    private double reboundProbability;
    private String analysisResult;  // "STRONG_REBOUND", "POSSIBLE_REBOUND", "NO_REBOUND"
    private String message;
} 