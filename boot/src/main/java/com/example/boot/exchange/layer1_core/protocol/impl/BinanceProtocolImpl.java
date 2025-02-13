package com.example.boot.exchange.layer1_core.protocol.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BinanceProtocolImpl implements BinanceExchangeProtocol {
    
    private final ObjectMapper objectMapper;

    public BinanceProtocolImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Binance WebSocket 구독 메시지 포맷:
     * {
     *   "method": "SUBSCRIBE",
     *   "params": ["btcusdt@ticker"],  // lowercase, @ 구분자 사용
     *   "id": 1
     * }
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs) {
        List<String> channels = pairs.stream()
            .map(pair -> pair.formatForBinance() + "@ticker")  // ticker만 구독
            .collect(Collectors.toList());

        Map<String, Object> message = new HashMap<>();
        message.put("method", "SUBSCRIBE");
        message.put("params", channels);
        message.put("id", 1);

        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create subscribe message", e);
        }
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs) {
        List<String> channels = pairs.stream()
            .map(pair -> pair.formatForBinance() + "@ticker")  // ticker만 구독 해제
            .collect(Collectors.toList());

        Map<String, Object> message = new HashMap<>();
        message.put("method", "UNSUBSCRIBE");
        message.put("params", channels);
        message.put("id", 1);

        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create unsubscribe message", e);
        }
    }
    
    @Override
    public boolean supports(String exchange) {
        return "binance".equalsIgnoreCase(exchange);
    }

    @Override
    public String getExchangeName() {
        return "binance";
    }
} 