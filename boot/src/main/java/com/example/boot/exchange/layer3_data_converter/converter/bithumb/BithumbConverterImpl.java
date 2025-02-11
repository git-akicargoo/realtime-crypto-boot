package com.example.boot.exchange.layer3_data_converter.converter.bithumb;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
            JsonNode root = objectMapper.readTree(message.rawMessage());
            
            // 메시지 타입 체크
            if (!root.has("type") || !"transaction".equals(root.get("type").asText())) {
                log.info("[컨버터-1] 무시된 메시지: {}", message.rawMessage());
                return null;
            }

            // content와 list 체크
            JsonNode content = root.get("content");
            if (content == null || !content.has("list") || content.get("list").size() == 0) {
                log.info("[컨버터-2] 무시된 메시지 (거래 데이터 없음): {}", message.rawMessage());
                return null;
            }

            // 첫 번째 거래 데이터 사용
            JsonNode trade = content.get("list").get(0);
            log.info("[컨버터-3] 거래 데이터 수신: {}", trade);

            String symbol = trade.get("symbol").asText();  // "BTC_KRW" 형식
            String price = trade.get("contPrice").asText();
            String quantity = trade.get("contQty").asText();
            String contDtm = trade.get("contDtm").asText();  // "2025-02-12 01:07:12.309898" 형식

            // 날짜 형식 변환
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
            LocalDateTime dateTime = LocalDateTime.parse(contDtm, formatter);
            Instant timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant();

            // symbol을 base와 quote로 분리 (BTC_KRW -> KRW, BTC)
            String[] parts = symbol.split("_");
            CurrencyPair currencyPair = new CurrencyPair(parts[1], parts[0]);

            StandardExchangeData data = StandardExchangeData.builder()
                .exchange(message.exchange())
                .currencyPair(currencyPair)
                .price(new BigDecimal(price))
                .volume(new BigDecimal(quantity))
                .timestamp(timestamp)
                .metadata(new HashMap<>())
                .build();

            log.info("[컨버터-4] 변환 완료: exchange={}, pair={}, price={}, volume={}", 
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