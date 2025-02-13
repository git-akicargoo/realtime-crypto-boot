package com.example.boot.exchange.layer3_data_converter.model;

import java.math.BigDecimal;  // 정확한 수치 계산을 위해 BigDecimal 사용
import java.time.Instant;
import java.util.Map;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StandardExchangeData {
    private final String exchange;          // 거래소 이름
    private final CurrencyPair currencyPair;// 거래쌍
    private final BigDecimal price;         // 현재가
    private final BigDecimal volume;        // 거래량
    private final Instant timestamp;        // 타임스탬프
    // 24시간 통계 데이터 필드 추가
    private final BigDecimal highPrice;     // 고가
    private final BigDecimal lowPrice;      // 저가
    private final BigDecimal priceChange;   // 가격 변동
    private final BigDecimal priceChangePercent; // 등락률
    private final BigDecimal volume24h;     // 24시간 거래량
    private final Map<String, Object> metadata; // 거래소별 추가 데이터

    @Override
    public String toString() {
        return String.format("""
            ┌─────────────────────────────────────────────
            │ Exchange Data
            ├─────────────────────────────────────────────
            │ Exchange    : %s
            │ Pair       : %s
            │ Price      : %s
            │ Volume     : %s
            │ High       : %s
            │ Low        : %s
            │ Change     : %s
            │ Change%%    : %s
            │ Volume 24h : %s
            │ Timestamp  : %s
            │ Metadata   : %s
            └─────────────────────────────────────────────""",
            exchange,
            currencyPair,
            price,
            volume,
            highPrice,
            lowPrice,
            priceChange,
            priceChangePercent,
            volume24h,
            timestamp,
            metadata);
    }
} 