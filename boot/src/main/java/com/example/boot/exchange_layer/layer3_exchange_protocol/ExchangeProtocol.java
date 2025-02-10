package com.example.boot.exchange_layer.layer3_exchange_protocol;

import java.util.List;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

public interface ExchangeProtocol {
    String createSubscribeMessage(List<CurrencyPair> pairs, String messageFormat);
    String createUnsubscribeMessage(List<CurrencyPair> pairs, String messageFormat);
    boolean supports(String exchange);
} 