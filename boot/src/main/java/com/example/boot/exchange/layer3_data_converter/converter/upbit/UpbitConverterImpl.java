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
            JsonNode root = objectMapper.readTree(message.rawMessage());
            
            // 메시지 타입 체크 ("ty" 필드가 "trade"인지 확인)
            if (!root.has("ty") || !"trade".equals(root.get("ty").asText())) {
                log.debug("[컨버터-1] 무시된 메시지: {}", message.rawMessage());
                return null;
            }

            log.debug("[컨버터-2] 거래 데이터 수신: {}", message.rawMessage());

            String code = root.get("cd").asText();  // "KRW-BTC" 형식
            String price = root.get("tp").asText();  // trade price
            String volume = root.get("tv").asText();  // trade volume
            long timestamp = root.get("tms").asLong();  // timestamp

            // code를 base와 quote로 분리 (KRW-BTC -> KRW, BTC)
            String[] parts = code.split("-");
            CurrencyPair currencyPair = new CurrencyPair(parts[0], parts[1]);

            StandardExchangeData data = StandardExchangeData.builder()
                .exchange(message.exchange())
                .currencyPair(currencyPair)
                .price(new BigDecimal(price))
                .volume(new BigDecimal(volume))
                .timestamp(Instant.ofEpochMilli(timestamp))
                .metadata(new HashMap<>())
                .build();

            log.debug("[컨버터-3] 변환 완료: exchange={}, pair={}, price={}, volume={}", 
                data.getExchange(), 
                data.getCurrencyPair(),
                data.getPrice(), 
                data.getVolume());

            return data;
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