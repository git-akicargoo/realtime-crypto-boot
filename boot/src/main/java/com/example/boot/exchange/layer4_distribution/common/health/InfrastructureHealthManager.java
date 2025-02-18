package com.example.boot.exchange.layer4_distribution.common.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.factory.DistributionServiceFactory;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.health.ZookeeperHealthIndicator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableScheduling
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class InfrastructureHealthManager {
    private final List<HealthCheckable> healthCheckers;
    private final ApplicationEventPublisher eventPublisher;
    private final DistributionServiceFactory distributionServiceFactory;
    private final Map<String, InfrastructureStatus> healthStatuses = new ConcurrentHashMap<>();
    
    private volatile boolean lastKafkaStatus = false;
    private volatile boolean lastZookeeperStatus = false;

    public InfrastructureHealthManager(
        List<HealthCheckable> healthCheckers,
        ApplicationEventPublisher eventPublisher,
        DistributionServiceFactory distributionServiceFactory
    ) {
        this.healthCheckers = healthCheckers;
        this.eventPublisher = eventPublisher;
        this.distributionServiceFactory = distributionServiceFactory;
        
        if (healthCheckers.isEmpty()) {
            log.warn("No health checkers registered. This might be because Kafka is disabled.");
        } else {
            log.info("Initialized with {} health checkers", healthCheckers.size());
            healthCheckers.forEach(checker -> 
                log.info("Registered health checker: {}", checker.getServiceName())
            );
        }
    }
    
    @Scheduled(fixedRateString = "${infrastructure.health-check.interval:10000}")
    public void checkAllHealth() {
        if (healthCheckers.isEmpty()) {
            return;
        }

        boolean currentKafkaStatus = false;
        boolean currentZookeeperStatus = false;
        List<InfrastructureStatus> statuses = new ArrayList<>();

        for (HealthCheckable checker : healthCheckers) {
            InfrastructureStatus status = checker.checkHealth();
            statuses.add(status);
            healthStatuses.put(checker.getServiceName(), status);
            
            boolean isAvailable = checker.isAvailable();
            
            if (checker instanceof KafkaHealthIndicator) {
                currentKafkaStatus = isAvailable;
            } else if (checker instanceof ZookeeperHealthIndicator) {
                currentZookeeperStatus = isAvailable;
            }
        }

        // 상태가 변경된 경우에만 이벤트 발행
        if (currentKafkaStatus != lastKafkaStatus || currentZookeeperStatus != lastZookeeperStatus) {
            log.info("Infrastructure status changed - Kafka: {} -> {}, Zookeeper: {} -> {}", 
                lastKafkaStatus, currentKafkaStatus,
                lastZookeeperStatus, currentZookeeperStatus);
                
            InfrastructureStatusChangeEvent event = new InfrastructureStatusChangeEvent(
                currentKafkaStatus, currentZookeeperStatus);
            
            eventPublisher.publishEvent(event);
            
            lastKafkaStatus = currentKafkaStatus;
            lastZookeeperStatus = currentZookeeperStatus;
        }

        // 상태 로깅
        logHealthStatus(statuses);
    }
    
    private void logHealthStatus(List<InfrastructureStatus> statuses) {
        // 각 서비스의 상태를 한 줄로 표현
        statuses.forEach(status -> {
            String statusEmoji = "CONNECTED".equals(status.getStatus()) ? "🟢" : "🔴";
            String details = status.getDetails().entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            
            log.info("{} {} | Status:{} | Target:{} | {}",
                statusEmoji,
                status.getServiceName(),
                status.getStatus(),
                status.getTarget(),
                details
            );
        });

        // 현재 서비스 상태도 한 줄로
        DistributionService currentService = distributionServiceFactory.getCurrentService();
        String currentServiceName = currentService != null ? 
            currentService.getClass().getSimpleName() : "No active service";
        boolean isDistributing = currentService != null ? currentService.isDistributing() : false;
        
        log.info("📌 Current Service: {} (Distributing: {})", 
            currentServiceName, isDistributing);
    }
    
    public Map<String, InfrastructureStatus> getCurrentStatus() {
        return new HashMap<>(healthStatuses);
    }
} 