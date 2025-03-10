package com.example.boot.exchange.layer6_analysis.dto;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AnalysisRequest {
    private String action;           // 웹소켓 액션 (startAnalysis, stopAnalysis 등)
    private String exchange;         // 거래소
    private String currencyPair;     // 거래쌍 (호환성 유지)
    private String symbol;           // 코인 심볼
    private String quoteCurrency;    // 화폐
    private String tradingStyle = "dayTrading"; // 기본값: 단타 트레이딩
    
    // 웹소켓 프론트엔드 통신용 추가 필드
    private String cardId;           // 카드 ID
    private String shortId;          // 짧은 ID
    private String createdAt;        // 생성 시간
    
    private double priceDropThreshold = 3.0;
    private double volumeIncreaseThreshold = 10.0;
    private int smaShortPeriod = 5;
    private int smaMediumPeriod = 15;
    private int smaLongPeriod = 30;
    private int rsiPeriod = 14;
    private int rsiOverbought = 70;
    private int rsiOversold = 30;
    private int bollingerPeriod = 20;
    private double bollingerDeviation = 2.0;
    
    // CurrencyPair 객체 생성 메서드
    public CurrencyPair toCurrencyPair() {
        if (symbol != null && quoteCurrency != null) {
            return new CurrencyPair(quoteCurrency, symbol);
        } else if (currencyPair != null && currencyPair.contains("-")) {
            String[] parts = currencyPair.split("-");
            return new CurrencyPair(parts[0], parts[1]);
        }
        return null;
    }
    
    // 문자열 형태의 통화쌍 반환
    public String getCurrencyPairString() {
        if (symbol != null && quoteCurrency != null) {
            return quoteCurrency + "-" + symbol;
        }
        return currencyPair;
    }
} 