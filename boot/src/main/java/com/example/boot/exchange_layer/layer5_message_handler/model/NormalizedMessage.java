package com.example.boot.exchange_layer.layer5_message_handler.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class NormalizedMessage {
    private final String exchange;
    private final String symbol;
    private final String quoteCurrency;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant timestamp;
    private final String tradeId;
} 