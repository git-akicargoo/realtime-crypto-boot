package com.example.boot.exchange.layer6_analysis.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분석 카드의 기본 정보를 담는 DTO 클래스
 * 모의 거래 시 카드 선택 드롭다운에 표시될 정보를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardInfoDTO {
    private String cardId;           // 카드 고유 ID
    private String exchange;         // 거래소 (Upbit, Binance 등)
    private String symbol;           // 코인 심볼 (BTC, ETH 등)
    private String quoteCurrency;    // 기준 화폐 (KRW, USDT 등)
    private String currencyPair;     // 거래쌍 (KRW-BTC, USDT-ETH 등)
    private String tradingStyle;     // 트레이딩 스타일 (dayTrading, swingTrading 등)
    private double currentPrice;     // 현재 가격
    private LocalDateTime createdAt; // 카드 생성 시간
    private long timestamp;          // 타임스탬프 (밀리초)
    
    // 최신 분석 결과 요약 정보
    private double buySignalStrength;    // 매수 신호 강도
    private double reboundProbability;   // 반등 확률
    private String marketCondition;      // 시장 상태 (BULLISH, BEARISH, NEUTRAL)
} 