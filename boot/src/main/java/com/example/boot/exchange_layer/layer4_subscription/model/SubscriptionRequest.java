package com.example.boot.exchange_layer.layer4_subscription.model;

import java.util.List;
import java.util.Set;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionRequest {
    private final String exchange;
    private final List<CurrencyPair> pairs;
    private final String messageFormat;
    private final Set<String> supportedSymbols;
    private final Set<String> supportedQuoteCurrencies;
} 