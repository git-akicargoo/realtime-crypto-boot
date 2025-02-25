package com.example.boot.exchange.layer6_analysis.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AnalysisRequest {
    private String exchange;           // 거래소
    private String currencyPair;       // 거래쌍
    private double priceDropThreshold; // 가격 하락 임계값
    private double volumeIncreaseThreshold; // 거래량 증가 임계값
} 