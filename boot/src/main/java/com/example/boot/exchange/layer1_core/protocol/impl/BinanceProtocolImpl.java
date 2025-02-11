package com.example.boot.exchange.layer1_core.protocol.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;

@Component
public class BinanceProtocolImpl implements BinanceExchangeProtocol {
    
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
        // 각 페어를 "btcusdt@trade" 형식의 문자열로 변환하고 쌍따옴표로 감싸기
        String params = pairs.stream()
            .map(pair -> "\"" + pair.formatForBinance() + "@trade" + "\"")
            .collect(Collectors.joining(","));
        
        // 최종 메시지 생성
        return String.format(
            "{\"method\":\"SUBSCRIBE\",\"params\":[%s],\"id\":1}",
            params
        );
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String params = pairs.stream()
            .map(pair -> pair.formatForBinance() + "@trade")
            .collect(Collectors.joining("\",\""));
            
        return String.format(messageFormat, params);
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