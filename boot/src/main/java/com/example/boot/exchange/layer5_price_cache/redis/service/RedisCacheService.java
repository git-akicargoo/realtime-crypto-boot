package com.example.boot.exchange.layer5_price_cache.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.monitor.RedisCacheMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {
    private static final Duration ANALYSIS_TTL = Duration.ofMinutes(30);  // 30분 분석 윈도우
    private static final String ANALYSIS_KEY_PREFIX = "analysis:";
    private static final String MARKET_DATA_CHANNEL_PREFIX = "market-data:";
    private static final int MAX_WINDOW_SIZE = 1000;  // 최대 데이터 포인트
    
    private final StringRedisTemplate redisTemplate;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheMonitor monitor;
    private final ScheduledLogger scheduledLogger;
    
    // 활성 구독 관리
    private final Map<String, ReactiveSubscription> activeSubscriptions = new ConcurrentHashMap<>();

    public void cachePrice(StandardExchangeData data) {
        try {
            String baseKey = generateBaseKey(data.getExchange(), data.getCurrencyPair().toString());
            String timeSeriesKey = baseKey + ":timeseries";
            
            // 채널 이름 형식 통일 (exchange:currencyPair)
            String channelName = MARKET_DATA_CHANNEL_PREFIX + data.getExchange().toLowerCase() + ":" + data.getCurrencyPair().toString();
            
            String dataJson = objectMapper.writeValueAsString(data);
            
            // Redis에 데이터 저장
            redisTemplate.opsForList().rightPush(timeSeriesKey, dataJson);
            redisTemplate.opsForList().trim(timeSeriesKey, -MAX_WINDOW_SIZE, -1);
            redisTemplate.expire(timeSeriesKey, ANALYSIS_TTL);
            redisTemplate.opsForValue().set(baseKey + ":latest", dataJson, ANALYSIS_TTL);
            
            // 실시간 데이터 발행
            log.debug("Publishing to Redis channel: {}", channelName);
            redisTemplate.convertAndSend(channelName, dataJson);
            log.debug("Published data to Redis channel: {}", channelName);
            
            updateStatistics(baseKey, data);
            
            scheduledLogger.scheduleLog(log, "Redis cache update - Exchange: {}, Pair: {}, Price: {}", 
                data.getExchange(), data.getCurrencyPair(), data.getPrice());
            
            monitor.incrementCacheOperation(true);
            monitor.setTotalCachedItems(getCacheSize());
        } catch (Exception e) {
            log.error("Failed to cache price data: {}", e.getMessage(), e);
            monitor.incrementCacheError();
        }
    }

    public Flux<StandardExchangeData> subscribeToMarketData(String exchange, String currencyPair) {
        String channelName = MARKET_DATA_CHANNEL_PREFIX + exchange.toLowerCase() + ":" + currencyPair;
        log.info("Subscribing to market data channel: {}", channelName);
        
        return reactiveRedisTemplate.listenToChannel(channelName)
            .doOnSubscribe(subscription -> log.info("Subscribed to channel: {}", channelName))
            .doOnNext(message -> log.debug("Received message from channel {}: {}", channelName, message.getMessage()))
            .doOnError(error -> log.error("Error in subscription for channel {}: {}", channelName, error.getMessage()))
            .doOnCancel(() -> log.info("Cancelled subscription to channel: {}", channelName))
            .map(message -> {
                try {
                    StandardExchangeData data = objectMapper.readValue(message.getMessage(), StandardExchangeData.class);
                    log.debug("Parsed message from channel {}: {}", channelName, data);
                    return data;
                } catch (Exception e) {
                    log.error("Failed to parse message from channel {}: {}", channelName, e.getMessage());
                    return null;
                }
            })
            .filter(data -> data != null);
    }

    public void unsubscribeFromMarketData(String exchange, String currencyPair) {
        String channelName = MARKET_DATA_CHANNEL_PREFIX + exchange + ":" + currencyPair;
        log.info("Unsubscribing from market data channel: {}", channelName);
        // 구독 취소는 클라이언트 측에서 Flux.dispose()를 호출하여 처리됩니다.
        // 여기서는 로깅만 수행합니다.
    }

    private void updateStatistics(String baseKey, StandardExchangeData data) {
        try {
            String statsKey = baseKey + ":stats";
            Map<String, String> stats = new HashMap<>();
            
            // 기본 통계 업데이트
            stats.put("lastPrice", data.getPrice().toString());
            stats.put("lastUpdate", data.getTimestamp().toString());
            stats.put("exchange", data.getExchange());
            stats.put("pair", data.getCurrencyPair().toString());
            
            redisTemplate.opsForHash().putAll(statsKey, stats);
            redisTemplate.expire(statsKey, ANALYSIS_TTL);
        } catch (Exception e) {
            log.error("Failed to update statistics: {}", e.getMessage());
        }
    }

    public List<StandardExchangeData> getAnalysisWindow(String exchange, String currencyPair) {
        String timeSeriesKey = generateBaseKey(exchange, currencyPair) + ":timeseries";
        
        // Redis에 저장된 모든 키 출력
        Set<String> allKeys = redisTemplate.keys("analysis:*");
        log.info("=== Redis Cache Debug ===");
        log.info("All Redis keys: {}", allKeys);
        log.info("Looking for key: {}", timeSeriesKey);
        
        try {
            List<String> dataList = redisTemplate.opsForList().range(timeSeriesKey, 0, -1);
            log.info("Found data count: {}", dataList != null ? dataList.size() : 0);
            
            if (dataList == null || dataList.isEmpty()) {
                log.warn("No data found for key: {}", timeSeriesKey);
                return Collections.emptyList();
            }
            
            return dataList.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, StandardExchangeData.class);
                    } catch (Exception e) {
                        log.error("Failed to parse cached data: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get analysis window: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String generateBaseKey(String exchange, String currencyPair) {
        return ANALYSIS_KEY_PREFIX + exchange.toLowerCase() + ":" + currencyPair;
    }

    public long getCacheSize() {
        try {
            return redisTemplate.keys(ANALYSIS_KEY_PREFIX + "*").size();
        } catch (Exception e) {
            log.error("Failed to get cache size: {}", e.getMessage());
            return 0;
        }
    }

    public void cacheExchangeData(StandardExchangeData data) {
        log.info("Caching exchange data: {}", data);
        String baseKey = generateBaseKey(data.getExchange(), data.getCurrencyPair().toString());
        
        // 채널 이름 형식 통일 (exchange:currencyPair)
        String channelName = MARKET_DATA_CHANNEL_PREFIX + data.getExchange().toLowerCase() + ":" + data.getCurrencyPair().toString();
        
        try {
            // 시계열 데이터 저장
            String timeSeriesKey = baseKey + ":timeseries";
            String dataJson = objectMapper.writeValueAsString(data);
            
            // 리스트의 오른쪽에 추가하고 왼쪽의 오래된 데이터는 제거
            redisTemplate.opsForList().rightPush(timeSeriesKey, dataJson);
            redisTemplate.opsForList().trim(timeSeriesKey, -MAX_WINDOW_SIZE, -1);
            redisTemplate.expire(timeSeriesKey, ANALYSIS_TTL);
            
            // 최신 가격 업데이트
            redisTemplate.opsForValue().set(baseKey + ":latest", dataJson, ANALYSIS_TTL);
            
            // 실시간 데이터 발행
            redisTemplate.convertAndSend(channelName, dataJson);
            
            // 통계 업데이트
            updateStatistics(baseKey, data);
            
            monitor.incrementCacheOperation(true);
            monitor.setTotalCachedItems(getCacheSize());
        } catch (Exception e) {
            log.error("Failed to cache analysis data: {}", e.getMessage());
            monitor.incrementCacheError();
        }
    }

    public StandardExchangeData getLatestData(String exchange, String currencyPair) {
        String timeSeriesKey = generateBaseKey(exchange, currencyPair) + ":timeseries";
        List<String> dataList = redisTemplate.opsForList().range(timeSeriesKey, -1, -1);
        
        if (dataList != null && !dataList.isEmpty()) {
            try {
                return objectMapper.readValue(dataList.get(0), StandardExchangeData.class);
            } catch (Exception e) {
                log.error("Error deserializing latest data", e);
            }
        }
        return null;
    }

    public void clearAnalysisData(String key) {
        redisTemplate.delete(key);
        log.info("Cleared analysis cache for key: {}", key);
    }
} 