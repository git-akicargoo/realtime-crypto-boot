package com.example.boot.exchange.layer1_core.protocol;

import java.util.List;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;

public interface BaseExchangeProtocol {
    String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat);
    String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat);
    boolean supports(String exchange);
    String getExchangeName();
} 