package com.example.boot.exchange.layer4_distribution.common.health;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class InfrastructureHealthManager {
    private final List<HealthCheckable> healthCheckers;
    private final Map<String, InfrastructureStatus> healthStatuses;
    private final boolean loggingEnabled;
    
    public InfrastructureHealthManager(
        List<HealthCheckable> healthCheckers,
        @Value("${infrastructure.health-check.logging.enabled:true}") boolean loggingEnabled
    ) {
        this.healthCheckers = healthCheckers;
        this.healthStatuses = new ConcurrentHashMap<>();
        this.loggingEnabled = loggingEnabled;
    }
    
    @Scheduled(fixedRateString = "${infrastructure.health-check.logging.interval:10000}")
    public void checkAllHealth() {
        List<InfrastructureStatus> currentStatuses = healthCheckers.stream()
            .map(checker -> {
                InfrastructureStatus status = checker.checkHealth();
                healthStatuses.put(checker.getServiceName(), status);
                return status;
            })
            .collect(Collectors.toList());
            
        if (loggingEnabled) {
            logHealthStatuses(currentStatuses);
        }
    }
    
    private void logHealthStatuses(List<InfrastructureStatus> statuses) {
        StringBuilder sb = new StringBuilder("Infrastructure Health Status [" + System.currentTimeMillis() + "]\n");
        
        statuses.forEach(status -> {
            String statusEmoji = "CONNECTED".equals(status.getStatus()) ? "🟢" : "🔴";
            
            sb.append(String.format("├─ %s (%s %s)\n", status.getServiceName(), statusEmoji, status.getStatus()))
              .append(String.format("│  ├─ Target: %s\n", status.getTarget()));
              
            status.getDetails().forEach((key, value) -> 
                sb.append(String.format("│  ├─ %s: %s\n", key, value))
            );
        });
        
        log.info(sb.toString());
    }
    
    public Map<String, InfrastructureStatus> getCurrentStatus() {
        return new HashMap<>(healthStatuses);
    }
} 