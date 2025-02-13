package com.example.boot.exchange.layer1_core.protocol.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.UpbitExchangeProtocol;

@Component
public class UpbitProtocolImpl implements UpbitExchangeProtocol {
    
    /**
     * Upbit WebSocket 구독 메시지 포맷:
     * [
     *   {
     *     "ticket": "UNIQUE_TICKET"
     *   },
     *   {
     *     "type": "ticker",
     *     "codes": ["KRW-BTC"]  // 대문자, - 구분자 사용
     *   },
     *   {
     *     "format": "SIMPLE"
     *   }
     * ]
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs) {
        String codes = pairs.stream()
            .map(CurrencyPair::formatForUpbit)
            .collect(Collectors.joining("\",\"", "\"", "\""));
        
        return String.format(
            "[{\"ticket\":\"UNIQUE_TICKET\"}," +
            "{\"type\":\"ticker\",\"codes\":[%s]}," +
            "{\"format\":\"SIMPLE\"}]",
            codes
        );
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs) {
        String codes = pairs.stream()
            .map(CurrencyPair::formatForUpbit)
            .collect(Collectors.joining("\",\"", "\"", "\""));
        
        return String.format(
            "[{\"ticket\":\"UNIQUE_TICKET\"}," +
            "{\"type\":\"ticker\",\"codes\":[%s]}," +
            "{\"format\":\"SIMPLE\"}]",
            codes
        );
    }
    
    @Override
    public boolean supports(String exchange) {
        return "upbit".equalsIgnoreCase(exchange);
    }

    @Override
    public String getExchangeName() {
        return "upbit";
    }
} 