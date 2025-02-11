package com.example.boot.exchange.layer3_data_converter.converter;

import com.example.boot.exchange.layer1_core.model.ExchangeMessage;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import reactor.core.publisher.Mono;

public interface ExchangeDataConverter {
    /**
     * 거래소 메시지를 표준 형식으로 변환
     */
    Mono<StandardExchangeData> convert(ExchangeMessage message);
    
    /**
     * 지원하는 거래소 이름
     */
    String getExchangeName();
} 