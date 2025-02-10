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
public class BinanceMessageHandler implements MessageHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<NormalizedMessage> handleMessage(String message) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode root = objectMapper.readTree(message);
                
                // Binance trade message format:
                // {
                //   "e": "trade",         // Event type
                //   "s": "BTCUSDT",       // Symbol
                //   "p": "50000.00",      // Price
                //   "q": "0.001",         // Quantity
                //   "T": 1645678912345,   // Trade time
                //   "t": 12345            // Trade ID
                // }
                
                String symbol = root.get("s").asText();
                String[] parts = symbol.split("USDT|BTC|ETH");
                
                return NormalizedMessage.builder()
                    .exchange("binance")
                    .symbol(parts[0])
                    .quoteCurrency(symbol.substring(parts[0].length()))
                    .price(new BigDecimal(root.get("p").asText()))
                    .quantity(new BigDecimal(root.get("q").asText()))
                    .timestamp(Instant.ofEpochMilli(root.get("T").asLong()))
                    .tradeId(root.get("t").asText())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to parse Binance message: {}", message, e);
                throw new RuntimeException("Message parsing failed", e);
            }
        });
    }
    
    @Override
    public boolean supports(String exchange) {
        return "binance".equalsIgnoreCase(exchange);
    }
} 