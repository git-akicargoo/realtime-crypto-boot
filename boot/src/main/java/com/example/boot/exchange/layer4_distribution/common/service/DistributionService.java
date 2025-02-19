package com.example.boot.exchange.layer4_distribution.common.service;

import java.util.Map;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public interface DistributionService {
    /**
     * 거래소 데이터 배포 시작
     */
    Flux<StandardExchangeData> startDistribution();
    
    /**
     * 특정 클라이언트에 데이터 전송
     */
    Mono<Void> sendToClient(String clientId, StandardExchangeData data);
    
    /**
     * 배포 중지
     */
    Mono<Void> stopDistribution();
    
    /**
     * 현재 배포 상태 확인
     */
    boolean isDistributing();
    
    // 새로 추가되는 메서드들
    Map<String, Sinks.Many<StandardExchangeData>> getActiveSinks();
    void restoreSinks(Map<String, Sinks.Many<StandardExchangeData>> sinks);
} 