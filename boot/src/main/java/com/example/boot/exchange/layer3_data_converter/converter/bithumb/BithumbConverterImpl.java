package com.example.boot.exchange.layer3_data_converter.converter.bithumb;

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
public class BithumbConverterImpl implements BithumbConverter {
    private final ObjectMapper objectMapper;

    public BithumbConverterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<StandardExchangeData> convert(ExchangeMessage message) {
        return Mono.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(message.rawMessage());
            String type = node.get("type").asText();

            // Ticker 데이터만 처리
            if ("ticker".equals(type)) {
                JsonNode content = node.get("content");
                String symbol = content.get("symbol").asText();
                String[] currencies = symbol.split("_");
                String baseSymbol = currencies[0];    // BTC, ETH 등
                String quoteCurrency = currencies[1]; // KRW

                return StandardExchangeData.builder()
                    .exchange(EXCHANGE_NAME)
                    .currencyPair(new CurrencyPair(quoteCurrency, baseSymbol))
                    .price(new BigDecimal(content.get("closePrice").asText()))
                    .volume(new BigDecimal(content.get("volume").asText()))
                    .highPrice(new BigDecimal(content.get("highPrice").asText()))
                    .lowPrice(new BigDecimal(content.get("lowPrice").asText()))
                    .priceChange(new BigDecimal(content.get("chgAmt").asText()))
                    .priceChangePercent(new BigDecimal(content.get("chgRate").asText()))
                    .volume24h(new BigDecimal(content.get("volume").asText()))
                    .timestamp(Instant.now())
                    .metadata(new HashMap<>())
                    .build();
            }

            return null;
        })
        .filter(Objects::nonNull)
        .onErrorResume(e -> {
            log.error("[컨버터-5] 변환 실패: {}", e.getMessage());
            return Mono.empty();
        });
    }

    @Override
    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
} 