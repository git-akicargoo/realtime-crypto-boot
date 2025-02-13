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
            JsonNode root = objectMapper.readTree(message.rawMessage());
            
            // 구독 응답 메시지 체크
            if (root.has("result")) {
                log.debug("[컨버터-1] 구독 응답 수신: {}", message.rawMessage());
                return null;
            }

            // 실제 거래 데이터 처리
            if (!root.has("e") || !"trade".equals(root.get("e").asText())) {
                log.debug("[컨버터-2] 무시된 메시지: {}", message.rawMessage());
                return null;
            }

            log.debug("[컨버터-3] 거래 데이터 수신: {}", message.rawMessage());

            String symbol = root.get("s").asText();
            String price = root.get("p").asText();
            String quantity = root.get("q").asText();
            long timestamp = root.get("T").asLong();

            String base = "";
            String currency = "";
            
            // 지원하는 base currency 확인
            if (symbol.endsWith("USDT")) {
                base = "USDT";
                currency = symbol.substring(0, symbol.length() - base.length());
            } else if (symbol.endsWith("BTC")) {
                base = "BTC";
                currency = symbol.substring(0, symbol.length() - base.length());
            } else {
                log.warn("[컨버터] 지원하지 않는 심볼 형식: {}", symbol);
                return null;
            }

            StandardExchangeData data = StandardExchangeData.builder()
                .exchange(message.exchange())
                .currencyPair(new CurrencyPair(base, currency))
                .price(new BigDecimal(price))
                .volume(new BigDecimal(quantity))
                .timestamp(Instant.ofEpochMilli(timestamp))
                .metadata(new HashMap<>())
                .build();

            log.debug("[컨버터-4] 변환 완료: exchange={}, pair={}, price={}, volume={}", 
                data.getExchange(), 
                data.getCurrencyPair(),
                data.getPrice(), 
                data.getVolume());

            return data;
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