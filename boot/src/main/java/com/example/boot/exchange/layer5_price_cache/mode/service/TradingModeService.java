package com.example.boot.exchange.layer5_price_cache.mode.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.factory.DistributionServiceFactory;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.service.KafkaDistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;
import com.example.boot.exchange.layer5_price_cache.redis.health.RedisHealthIndicator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TradingModeService {
    private final DistributionServiceFactory distributionServiceFactory;
    private final RedisHealthIndicator redisHealthIndicator;
    private final LeaderElectionService leaderElectionService;
    private final ScheduledLogger scheduledLogger;
    private final ApplicationEventPublisher eventPublisher;

    public TradingModeService(
            @Qualifier("kafkaDistributionService") DistributionService distributionService,
            RedisHealthIndicator redisHealthIndicator,
            LeaderElectionService leaderElectionService,
            ScheduledLogger scheduledLogger,
            DistributionServiceFactory distributionServiceFactory,
            ApplicationEventPublisher eventPublisher) {
        this.distributionServiceFactory = distributionServiceFactory;
        this.redisHealthIndicator = redisHealthIndicator;
        this.leaderElectionService = leaderElectionService;
        this.scheduledLogger = scheduledLogger;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = 10000)  // 10초마다 체크
    public void checkAndUpdateMode() {
        // Redis 상태와 관계없이 Kafka 모드와 리더 상태만 체크
        boolean isValid = isKafkaMode() && isLeader();
        if (!isValid) {
            log.info("Infrastructure status changed - Leader: {}", isLeader());
            // eventPublisher.publishEvent(new InfrastructureStatusChangeEvent(false, false));
        }
    }

    public boolean isValidMode() {
        boolean kafkaOk = isKafkaMode();
        boolean leaderOk = isLeader();
        // Redis 상태는 로깅만 하고 판단에는 사용하지 않음
        boolean redisOk = isRedisAvailable();
        
        // Redis 상태와 관계없이 Kafka 모드이고 리더면 valid
        boolean isValid = kafkaOk && leaderOk;
        
        scheduledLogger.scheduleLog(log, "Trading mode status - Valid: {}, Kafka: {}, Redis: {}, Leader: {}", 
            isValid, kafkaOk, redisOk, leaderOk);
        return isValid;
    }

    private boolean isKafkaMode() {
        return distributionServiceFactory.getCurrentService() instanceof KafkaDistributionService;
    }

    private boolean isRedisAvailable() {
        return redisHealthIndicator.health().getStatus() == Status.UP;
    }

    private boolean isLeader() {
        return leaderElectionService.isLeader();
    }
} 