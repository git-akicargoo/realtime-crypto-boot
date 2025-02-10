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
public class BithumbMessageHandler implements MessageHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<NormalizedMessage> handleMessage(String message) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode root = objectMapper.readTree(message);
                
                // Bithumb trade message format:
                // {
                //   "type": "trade",
                //   "symbol": "BTC_KRW",    // Symbol
                //   "price": "60000000",    // Price
                //   "quantity": "0.001",    // Quantity
                //   "timestamp": 1645678912345,
                //   "trade_id": "12345"     // Trade ID
                // }
                
                String symbol = root.get("symbol").asText();
                String[] parts = symbol.split("_");
                
                return NormalizedMessage.builder()
                    .exchange("bithumb")
                    .symbol(parts[0])
                    .quoteCurrency(parts[1])
                    .price(new BigDecimal(root.get("price").asText()))
                    .quantity(new BigDecimal(root.get("quantity").asText()))
                    .timestamp(Instant.ofEpochMilli(root.get("timestamp").asLong()))
                    .tradeId(root.get("trade_id").asText())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to parse Bithumb message: {}", message, e);
                throw new RuntimeException("Message parsing failed", e);
            }
        });
    }
    
    @Override
    public boolean supports(String exchange) {
        return "bithumb".equalsIgnoreCase(exchange);
    }
} 