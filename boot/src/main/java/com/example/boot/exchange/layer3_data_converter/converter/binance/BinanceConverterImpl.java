package com.example.boot.exchange.layer3_data_converter.converter.binance;

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
public class BinanceConverterImpl implements BinanceConverter {
    private final ObjectMapper objectMapper;

    @Override
    public Mono<StandardExchangeData> convert(ExchangeMessage message) {
        return Mono.fromCallable((Callable<StandardExchangeData>) () -> {
            try {
                JsonNode node = objectMapper.readTree(message.rawMessage());
                
                String symbol = node.get("s").asText(); // ex: "BTCUSDT"
                String base = symbol.substring(0, 3);   // "BTC"
                String quote = symbol.substring(3);     // "USDT"
                
                return StandardExchangeData.builder()
                    .exchange(getExchangeName())
                    .currencyPair(new CurrencyPair(quote, base))
                    .price(new BigDecimal(node.get("p").asText()))
                    .volume(new BigDecimal(node.get("q").asText()))
                    .timestamp(message.timestamp())
                    .metadata(new HashMap<>())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to convert Binance message: {}", e.getMessage());
                throw new RuntimeException("Message conversion failed", e);
            }
        });
    }

    @Override
    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
} 