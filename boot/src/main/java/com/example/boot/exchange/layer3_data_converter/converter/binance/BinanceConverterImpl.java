package com.example.boot.exchange.layer3_data_converter.converter.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.model.ExchangeMessage;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class BinanceConverterImpl implements BinanceConverter {
    private final ObjectMapper objectMapper;

    public BinanceConverterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<StandardExchangeData> convert(ExchangeMessage message) {
        return Mono.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(message.rawMessage());
            String eventType = node.get("e").asText();

            // 24시간 Ticker 데이터만 처리
            if ("24hrTicker".equals(eventType)) {
                String symbol = node.get("s").asText();
                String[] currencies = symbol.toUpperCase().split("USDT");
                String baseSymbol = currencies[0];  // BTC, ETH 등

                return StandardExchangeData.builder()
                    .exchange(EXCHANGE_NAME)
                    .currencyPair(new CurrencyPair("USDT", baseSymbol))
                    .price(new BigDecimal(node.get("c").asText()))        // closePrice
                    .volume(new BigDecimal(node.get("v").asText()))       // volume
                    .highPrice(new BigDecimal(node.get("h").asText()))    // highPrice
                    .lowPrice(new BigDecimal(node.get("l").asText()))     // lowPrice
                    .priceChange(new BigDecimal(node.get("p").asText()))  // priceChange
                    .priceChangePercent(new BigDecimal(node.get("P").asText())) // priceChangePercent
                    .volume24h(new BigDecimal(node.get("v").asText()))    // volume24h
                    .timestamp(Instant.ofEpochMilli(node.get("E").asLong()))
                    .metadata(new HashMap<>())
                    .build();
            }

            return null;
        })
        .filter(Objects::nonNull)
        .onErrorResume(e -> {
            log.error("Failed to convert message: {}", e.getMessage());
            return Mono.empty();
        });
    }

    @Override
    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
} 