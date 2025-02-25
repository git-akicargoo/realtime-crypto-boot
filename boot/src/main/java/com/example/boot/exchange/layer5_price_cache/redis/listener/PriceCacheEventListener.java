package com.example.boot.exchange.layer5_price_cache.redis.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.mode.service.TradingModeService;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PriceCacheEventListener {
    private final TradingModeService tradingModeService;
    private final RedisCacheService cacheService;
    private final ScheduledLogger scheduledLogger;

    public PriceCacheEventListener(
            TradingModeService tradingModeService, 
            RedisCacheService cacheService,
            ScheduledLogger scheduledLogger) {
        this.tradingModeService = tradingModeService;
        this.cacheService = cacheService;
        this.scheduledLogger = scheduledLogger;
    }

    @KafkaListener(topics = "${spring.kafka.topics.trades}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePriceEvent(StandardExchangeData data) {
        scheduledLogger.scheduleLog(log, "Kafka events - Exchange: {}, Pair: {}, Price: {}, Mode valid: {}", 
            data.getExchange(), data.getCurrencyPair(), data.getPrice(), tradingModeService.isValidMode());
            
        if (tradingModeService.isValidMode()) {
            cacheService.cachePrice(data);
        }
    }
} 