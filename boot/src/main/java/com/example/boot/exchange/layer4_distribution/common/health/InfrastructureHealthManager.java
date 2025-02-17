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
            log.warn("No health checkers registered. Health check skipped.");
            return;
        }

        log.info("Starting health check...");
        
        boolean currentKafkaStatus = false;
        boolean currentZookeeperStatus = false;
        List<InfrastructureStatus> statuses = new ArrayList<>();

        for (HealthCheckable checker : healthCheckers) {
            InfrastructureStatus status = checker.checkHealth();
            statuses.add(status);
            healthStatuses.put(checker.getServiceName(), status);
            
            boolean isAvailable = checker.isAvailable();
            log.info("Health check for {}: {}", checker.getServiceName(), isAvailable ? "AVAILABLE" : "UNAVAILABLE");
            
            if (checker instanceof KafkaHealthIndicator) {
                currentKafkaStatus = isAvailable;
            } else if (checker instanceof ZookeeperHealthIndicator) {
                currentZookeeperStatus = isAvailable;
            }
        }

        // 현재 인프라 상태 로깅 및 이벤트 발행
        log.info("Current infrastructure status - Kafka:{}, Zookeeper:{}", 
            currentKafkaStatus, currentZookeeperStatus);
        
        InfrastructureStatusChangeEvent event = new InfrastructureStatusChangeEvent(
            currentKafkaStatus, currentZookeeperStatus);
        
        log.info("Publishing infrastructure status event: {}", event);
        eventPublisher.publishEvent(event);

        // 현재 사용 중인 서비스 정보 추가
        DistributionService currentService = distributionServiceFactory.getCurrentService();
        String currentServiceName = currentService != null ? 
            currentService.getClass().getSimpleName() : "No active service";
        boolean isDistributing = currentService != null ? currentService.isDistributing() : false;

        StringBuilder sb = new StringBuilder("\n🏥 Infrastructure Health Status\n");
        statuses.forEach(status -> {
            String statusEmoji = "CONNECTED".equals(status.getStatus()) ? "🟢" : "🔴";
            sb.append(String.format("├─ %s %s\n", statusEmoji, status.getServiceName()))
              .append(String.format("│  ├─ Status: %s\n", status.getStatus()))
              .append(String.format("│  ├─ Target: %s\n", status.getTarget()));
              
            status.getDetails().forEach((key, value) -> 
                sb.append(String.format("│  ├─ %s: %s\n", key, value))
            );
        });
        
        // 현재 서비스 상태 추가
        sb.append(String.format("\n📌 Current Distribution Service\n"))
          .append(String.format("├─ Service: %s\n", currentServiceName))
          .append(String.format("└─ Distributing: %s\n", isDistributing));
        
        log.info(sb.toString());
    }
    
    public Map<String, InfrastructureStatus> getCurrentStatus() {
        return new HashMap<>(healthStatuses);
    }
} 