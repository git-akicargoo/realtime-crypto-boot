package com.example.boot.exchange.layer1_core.model;

import java.time.Instant;

public record ExchangeMessage(
    String exchange,
    String rawMessage,
    Instant timestamp,
    MessageType type
) {
    public enum MessageType {
        TICKER,
        HEARTBEAT,
        ERROR,
        SUBSCRIBE,
        UNSUBSCRIBE
    }
} 