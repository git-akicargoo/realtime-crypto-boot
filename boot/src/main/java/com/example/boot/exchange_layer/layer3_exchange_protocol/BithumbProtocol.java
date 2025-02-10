package com.example.boot.exchange_layer.layer3_exchange_protocol;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

@Component
public class BithumbProtocol implements ExchangeProtocol {
    
    /**
     * Bithumb WebSocket 구독 메시지 포맷:
     * {
     *   "type": "transaction",
     *   "symbols": ["BTC_KRW"]  // 대문자, _ 구분자 사용
     * }
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String symbols = pairs.stream()
            .map(CurrencyPair::formatForBithumb)
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, symbols);
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String symbols = pairs.stream()
            .map(CurrencyPair::formatForBithumb)
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, symbols);
    }
    
    @Override
    public boolean supports(String exchange) {
        return "bithumb".equalsIgnoreCase(exchange);
    }
} 