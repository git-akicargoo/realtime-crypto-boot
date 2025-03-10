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
    
    // 트레이딩 스타일
    private String tradingStyle;
    
    // 종합 매수 적합도 점수 (0-100%)
    private double buySignalStrength;
    
    // SMA 관련 필드
    private double sma1Difference;
    private double smaMediumDifference;
    private double sma3Difference;
    private boolean smaBreakout;
    private String smaSignal; // "BULLISH", "BEARISH", "NEUTRAL"
    
    // RSI 관련 필드
    private double rsiValue;
    private String rsiSignal; // "OVERBOUGHT", "OVERSOLD", "NEUTRAL"
    
    // 볼린저 밴드 관련 필드
    private double bollingerUpper;
    private double bollingerMiddle;
    private double bollingerLower;
    private String bollingerSignal; // "UPPER_TOUCH", "LOWER_TOUCH", "MIDDLE_CROSS", "INSIDE"
    private double bollingerWidth; // 밴드 폭 (변동성 지표)
    
    // 거래량 관련 필드
    private double volumeSignalStrength; // 거래량 신호 강도 (0-100%)
    
    // 과매수/과매도 상태
    private String marketCondition; // "OVERBOUGHT", "OVERSOLD", "NEUTRAL"
    private double marketConditionStrength; // 과매수/과매도 강도 (0-100%)

    // 프론트엔드 통합용 추가 필드
    private String cardId;
    private String shortId;
    private String createdAt;
    private long timestamp;  // 타임스탬프 (밀리초)

    public Double getSmaShortDifference() {
        return this.sma1Difference;
    }

    public Double getSmaLongDifference() {
        return this.sma3Difference;
    }

    public Double getSma1Difference() {
        return this.sma1Difference;
    }

    public Double getSma3Difference() {
        return this.sma3Difference;
    }
} 