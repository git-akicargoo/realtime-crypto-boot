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
     *     "type": "trade",
     *     "codes": ["KRW-BTC"]  // 대문자, - 구분자 사용
     *   }
     * ]
     */
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        // 각 페어를 "KRW-BTC" 형식의 문자열로 변환하고 쌍따옴표로 감싸기
        String codes = pairs.stream()
            .map(pair -> "\"" + pair.formatForUpbit() + "\"")
            .collect(Collectors.joining(","));
        
        // 최종 메시지 생성
        return String.format(
            "[{\"ticket\":\"UNIQUE_TICKET\"},{\"type\":\"trade\",\"codes\":[%s]},{\"format\":\"SIMPLE\"}]",
            codes
        );
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String codes = pairs.stream()
            .map(CurrencyPair::formatForUpbit)
            .collect(Collectors.joining("\",\""));
            
        return String.format(messageFormat, codes);
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