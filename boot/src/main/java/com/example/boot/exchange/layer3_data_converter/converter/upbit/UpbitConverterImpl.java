package com.example.boot.exchange.layer3_data_converter.converter.upbit;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.model.ExchangeMessage;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitConverterImpl implements UpbitConverter {
    private final ObjectMapper objectMapper;

    @Override
    public Mono<StandardExchangeData> convert(ExchangeMessage message) {
        return Mono.fromCallable((Callable<StandardExchangeData>) () -> {
            try {
                JsonNode node = objectMapper.readTree(message.rawMessage());
                
                String[] marketParts = node.get("code").asText().split("-"); // ex: "KRW-BTC"
                String quote = marketParts[0];  // "KRW"
                String base = marketParts[1];   // "BTC"
                
                return StandardExchangeData.builder()
                    .exchange(getExchangeName())
                    .currencyPair(new CurrencyPair(quote, base))
                    .price(new BigDecimal(node.get("trade_price").asText()))
                    .volume(new BigDecimal(node.get("trade_volume").asText()))
                    .timestamp(message.timestamp())
                    .metadata(new HashMap<>())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to convert Upbit message: {}", e.getMessage());
                throw new RuntimeException("Message conversion failed", e);
            }
        });
    }

    @Override
    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
} 