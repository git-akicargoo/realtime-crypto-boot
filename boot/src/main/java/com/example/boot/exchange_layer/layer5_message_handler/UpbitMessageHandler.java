package com.example.boot.exchange_layer.layer5_message_handler;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitMessageHandler implements MessageHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<NormalizedMessage> handleMessage(String message) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode root = objectMapper.readTree(message);
                
                // Upbit trade message format:
                // {
                //   "type": "trade",
                //   "code": "KRW-BTC",    // Market code
                //   "price": "60000000",   // Price
                //   "volume": "0.001",     // Quantity
                //   "timestamp": 1645678912345,
                //   "sequential_id": 12345  // Trade ID
                // }
                
                String marketCode = root.get("code").asText();
                String[] parts = marketCode.split("-");
                
                return NormalizedMessage.builder()
                    .exchange("upbit")
                    .symbol(parts[1])
                    .quoteCurrency(parts[0])
                    .price(new BigDecimal(root.get("price").asText()))
                    .quantity(new BigDecimal(root.get("volume").asText()))
                    .timestamp(Instant.ofEpochMilli(root.get("timestamp").asLong()))
                    .tradeId(root.get("sequential_id").asText())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to parse Upbit message: {}", message, e);
                throw new RuntimeException("Message parsing failed", e);
            }
        });
    }
    
    @Override
    public boolean supports(String exchange) {
        return "upbit".equalsIgnoreCase(exchange);
    }
} 