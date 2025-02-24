package com.example.boot.exchange.layer5_price_cache.redis.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.exchange.layer5_price_cache.redis.health.RedisHealthIndicator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisCacheMonitor {
    private final RedisHealthIndicator redisHealthIndicator;
    private final ScheduledLogger scheduledLogger;
    private final String redisHost;
    private final int redisPort;
    
    private long currentCacheOperationCount = 0;
    private long lastCacheOperationCount = 0;
    private long totalCachedItems = 0;
    private long errorCount = 0;

    public RedisCacheMonitor(
            RedisHealthIndicator redisHealthIndicator, 
            ScheduledLogger scheduledLogger,
            @Value("${spring.redis.host}") String redisHost,
            @Value("${spring.redis.port}") int redisPort) {
        this.redisHealthIndicator = redisHealthIndicator;
        this.scheduledLogger = scheduledLogger;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    public void incrementCacheOperation(boolean isUpdate) {
        currentCacheOperationCount++;
    }

    public void setTotalCachedItems(long count) {
        this.totalCachedItems = count;
    }

    public void incrementCacheError() {
        errorCount++;
    }

    @Scheduled(fixedRate = 10000)
    public void monitorStatus() {
        boolean isRedisUp = redisHealthIndicator.health().getStatus() == Status.UP;
        if (!isRedisUp) {
            resetCounters();
        }
        logStatus(isRedisUp);
    }

    private void resetCounters() {
        currentCacheOperationCount = 0;
        lastCacheOperationCount = 0;
        totalCachedItems = 0;
    }

    private void logStatus(boolean isRedisUp) {
        long totalOperations = currentCacheOperationCount - lastCacheOperationCount;
        lastCacheOperationCount = currentCacheOperationCount;
        
        StringBuilder status = new StringBuilder("\n📊 Analysis Cache Status\n");
        status.append("├─ Status: ").append(isRedisUp ? "🟢 CONNECTED" : "🔴 DISCONNECTED").append("\n");
        status.append("├─ Host: ").append(redisHost).append(":").append(redisPort).append("\n");
        status.append("├─ Cached Windows: ").append(isRedisUp ? totalCachedItems : 0).append("\n");
        status.append("├─ Operations (Last 10s): +").append(isRedisUp ? totalOperations : 0).append("\n");
        status.append("└─ Errors: ").append(errorCount);

        scheduledLogger.scheduleLog(log, status.toString());
    }
} 