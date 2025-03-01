package com.example.boot.exchange.layer6_analysis.dto;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AnalysisRequest {
    private String exchange;           // 거래소
    private String currencyPair;       // 거래쌍 (호환성 유지)
    private String symbol;             // 코인 심볼
    private String quoteCurrency;      // 화폐
    private double priceDropThreshold; // 가격 하락 임계값
    private double volumeIncreaseThreshold; // 거래량 증가 임계값
    private int smaShortPeriod;       // 단기 SMA 기간 (분)
    private int smaLongPeriod;        // 장기 SMA 기간 (분)
    
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