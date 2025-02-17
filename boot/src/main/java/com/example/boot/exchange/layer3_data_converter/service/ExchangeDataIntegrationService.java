package com.example.boot.exchange.layer3_data_converter.service;

import java.util.List;
import java.util.Map;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExchangeDataIntegrationService {
    Flux<StandardExchangeData> subscribe();
    Flux<StandardExchangeData> subscribe(Map<String, List<CurrencyPair>> exchangePairs);
    Mono<Void> unsubscribeAll();
    Mono<Void> unsubscribe(String exchange);
} 