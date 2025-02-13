package com.example.boot.exchange.layer1_core.protocol.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BithumbExchangeProtocol;

@Component
public class BithumbProtocolImpl implements BithumbExchangeProtocol {
    
    /**
     * Bithumb WebSocket 구독 메시지 포맷:
     * {
     *   "type": "ticker",
     *   "symbols": ["BTC_KRW"]  // 대문자, _ 구분자 사용
     * }
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs) {
        String symbols = pairs.stream()
            .map(pair -> String.format("%s_%s", pair.symbol(), pair.quoteCurrency()))
            .collect(Collectors.joining("\",\""));
        
        // ticker 구독 메시지만 사용
        return String.format(
            "{\"type\":\"ticker\",\"symbols\":[\"%s\"],\"tickTypes\":[\"24H\"]}",
            symbols
        );
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs) {
        // 구독 해제도 동일한 형식 사용
        return createSubscribeMessage(pairs);
    }
    
    @Override
    public boolean supports(String exchange) {
        return "bithumb".equalsIgnoreCase(exchange);
    }

    @Override
    public String getExchangeName() {
        return "bithumb";
    }
} 