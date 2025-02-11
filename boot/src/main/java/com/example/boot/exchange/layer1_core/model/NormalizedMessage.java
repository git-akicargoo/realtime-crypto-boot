package com.example.boot.exchange.layer1_core.model;

import java.math.BigDecimal;
import java.time.Instant;

public record NormalizedMessage(
    String exchange,
    String symbol,
    String quoteCurrency,
    BigDecimal price,
    BigDecimal quantity,
    Instant timestamp,
    String tradeId
) {
    public CurrencyPair getCurrencyPair() {
        return new CurrencyPair(quoteCurrency, symbol);
    }
} 