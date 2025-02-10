package com.example.boot.exchange_layer.layer3_exchange_protocol;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

@Component
public class BinanceProtocol implements ExchangeProtocol {
    
    /**
     * Binance WebSocket 구독 메시지 포맷:
     * {
     *   "method": "SUBSCRIBE",
     *   "params": ["btcusdt@trade"],  // lowercase, @ 구분자 사용
     *   "id": 1
     * }
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String params = pairs.stream()
            .map(pair -> (pair.getSymbol() + pair.getQuoteCurrency()).toLowerCase())
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, params);
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String params = pairs.stream()
            .map(pair -> (pair.getSymbol() + pair.getQuoteCurrency()).toLowerCase())
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, params);
    }
    
    @Override
    public boolean supports(String exchange) {
        return "binance".equalsIgnoreCase(exchange);
    }
} 