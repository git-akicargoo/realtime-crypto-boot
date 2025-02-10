package com.example.boot.exchange_layer.layer3_exchange_protocol;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

@Component
public class UpbitProtocol implements ExchangeProtocol {
    
    @Override
    public String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String codes = pairs.stream()
            .map(CurrencyPair::formatForUpbit)
            .map(pair -> String.format("\"%s\"", pair))
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, codes);
    }
    
    @Override
    public String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat) {
        String codes = pairs.stream()
            .map(CurrencyPair::formatForUpbit)
            .map(pair -> String.format("\"%s\"", pair))
            .collect(Collectors.joining(","));
            
        return String.format(messageFormat, codes);
    }
    
    @Override
    public boolean supports(String exchange) {
        return "upbit".equalsIgnoreCase(exchange);
    }
} 