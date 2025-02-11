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
     *   "type": "transaction",
     *   "symbols": ["BTC_KRW"]  // 대문자, _ 구분자 사용
     * }
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        // 각 페어를 "BTC_KRW" 형식의 문자열로 변환하고 쌍따옴표로 감싸기
        String symbols = pairs.stream()
            .map(CurrencyPair::formatForBithumb)
            .map(symbol -> "\"" + symbol + "\"")
            .collect(Collectors.joining(","));
        
        // 최종 메시지 생성
        return String.format(
            "{\"type\":\"transaction\",\"symbols\":[%s]}",
            symbols
        );
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String symbols = pairs.stream()
            .map(CurrencyPair::formatForBithumb)
            .collect(Collectors.joining("\",\""));
            
        return String.format(messageFormat, symbols);
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