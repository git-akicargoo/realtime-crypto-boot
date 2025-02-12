package com.example.boot.exchange.layer3_data_converter.service;

import java.util.List;
import java.util.Map;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExchangeDataIntegrationService {
    /**
     * 지정된 거래소와 화폐쌍에 대한 실시간 데이터 구독
     * @param exchangePairs 거래소별 구독할 화폐쌍 목록 (Map<거래소, List<화폐쌍>>)
     * @return 표준화된 거래소 데이터 스트림
     */
    Flux<StandardExchangeData> subscribe(Map<String, List<CurrencyPair>> exchangePairs);
    
    /**
     * 모든 구독 중지
     */
    Mono<Void> unsubscribeAll();
    
    /**
     * 특정 거래소 구독 중지
     */
    Mono<Void> unsubscribe(String exchange);
} 