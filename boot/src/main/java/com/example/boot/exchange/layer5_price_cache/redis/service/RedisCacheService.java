package com.example.boot.exchange.layer5_price_cache.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.monitor.RedisCacheMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisCacheService {
    private static final Duration ANALYSIS_TTL = Duration.ofMinutes(30);  // 30분 분석 윈도우
    private static final String ANALYSIS_KEY_PREFIX = "analysis:";
    private static final int MAX_WINDOW_SIZE = 1000;  // 최대 데이터 포인트
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheMonitor monitor;

    public RedisCacheService(
            RedisTemplate<String, String> redisTemplate, 
            ObjectMapper objectMapper,
            RedisCacheMonitor monitor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.monitor = monitor;
    }

    public void cachePrice(StandardExchangeData data) {
        String baseKey = generateBaseKey(data.getExchange(), data.getCurrencyPair().toString());
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
            
            // 통계 업데이트
            updateStatistics(baseKey, data);
            
            monitor.incrementCacheOperation(true);
            monitor.setTotalCachedItems(getCacheSize());
        } catch (Exception e) {
            log.error("Failed to cache analysis data: {}", e.getMessage());
            monitor.incrementCacheError();
        }
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
        try {
            List<String> dataList = redisTemplate.opsForList().range(timeSeriesKey, 0, -1);
            if (dataList == null || dataList.isEmpty()) {
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
        return ANALYSIS_KEY_PREFIX + exchange + ":" + currencyPair;
    }

    public long getCacheSize() {
        try {
            return redisTemplate.keys(ANALYSIS_KEY_PREFIX + "*").size();
        } catch (Exception e) {
            log.error("Failed to get cache size: {}", e.getMessage());
            return 0;
        }
    }
} 