package com.example.boot.exchange.layer5_price_cache.redis.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.common.logging.ScheduledLogger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;
    private final ScheduledLogger scheduledLogger;
    private Health health = Health.down().build();

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory, ScheduledLogger scheduledLogger) {
        this.connectionFactory = connectionFactory;
        this.scheduledLogger = scheduledLogger;
    }

    @Override
    public Health health() {
        return health;
    }

    @Scheduled(fixedRate = 10000)
    public void checkHealth() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            health = Health.up().build();
            scheduledLogger.scheduleLog(log, "Redis connection status - Status: {}", health.getStatus());
        } catch (Exception e) {
            health = Health.down(e).build();
            scheduledLogger.scheduleLog(log, "Redis connection status - Status: {}, Reason: {}", 
                health.getStatus(), e.getMessage());
        }
    }
} 