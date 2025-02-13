package com.example.boot.exchange.layer3_data_converter.converter.upbit;

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
public class UpbitConverterImpl implements UpbitConverter {
    private final ObjectMapper objectMapper;

    public UpbitConverterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<StandardExchangeData> convert(ExchangeMessage message) {
        return Mono.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(message.rawMessage());
            String type = node.get("ty").asText();

            // Ticker 데이터만 처리
            if ("ticker".equals(type)) {
                String code = node.get("cd").asText();
                String[] currencies = code.split("-");
                String quoteCurrency = currencies[0];  // KRW
                String baseSymbol = currencies[1];     // BTC, ETH 등

                return StandardExchangeData.builder()
                    .exchange(EXCHANGE_NAME)
                    .currencyPair(new CurrencyPair(quoteCurrency, baseSymbol))
                    .price(new BigDecimal(node.get("tp").asText()))
                    .volume(new BigDecimal(node.get("tv").asText()))
                    .highPrice(new BigDecimal(node.get("hp").asText()))
                    .lowPrice(new BigDecimal(node.get("lp").asText()))
                    .priceChange(new BigDecimal(node.get("cp").asText()))
                    .priceChangePercent(new BigDecimal(node.get("cr").asText()))
                    .volume24h(new BigDecimal(node.get("atv").asText()))
                    .timestamp(Instant.ofEpochMilli(node.get("tms").asLong()))
                    .metadata(new HashMap<>())
                    .build();
            }

            return null;
        })
        .filter(Objects::nonNull)
        .onErrorResume(e -> {
            log.error("[컨버터-4] 변환 실패: {}", e.getMessage());
            return Mono.empty();
        });
    }

    @Override
    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
} 