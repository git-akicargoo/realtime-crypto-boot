package com.example.boot.exchange.layer5_price_cache.redis.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.mode.service.TradingModeService;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PriceCacheEventListener {
    private final TradingModeService tradingModeService;
    private final RedisCacheService cacheService;

    public PriceCacheEventListener(TradingModeService tradingModeService, RedisCacheService cacheService) {
        this.tradingModeService = tradingModeService;
        this.cacheService = cacheService;
    }

    @KafkaListener(topics = "${spring.kafka.topics.trades}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePriceEvent(StandardExchangeData data) {
        if (tradingModeService.isValidMode()) {
            // 분석용 데이터 캐싱
            cacheService.cachePrice(data);
            
            // 실시간 데이터 로깅
            log.debug("📊 Analysis data cached - Exchange: {}, Pair: {}, Price: {}", 
                data.getExchange(), data.getCurrencyPair(), data.getPrice());
        }
    }
} 