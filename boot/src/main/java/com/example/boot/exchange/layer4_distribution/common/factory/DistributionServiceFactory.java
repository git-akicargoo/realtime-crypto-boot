package com.example.boot.exchange.layer4_distribution.common.factory;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.common.session.registry.SessionRegistry;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.direct.service.DirectDistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.health.ZookeeperHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.service.KafkaDistributionService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class DistributionServiceFactory {
    private final KafkaDistributionService kafkaService;
    private final DirectDistributionService directService;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final ZookeeperHealthIndicator zookeeperHealthIndicator;
    private final SessionRegistry sessionRegistry;
    private volatile DistributionService currentService;

    public DistributionServiceFactory(
        @Autowired(required = false) KafkaDistributionService kafkaService,
        DirectDistributionService directService,
        @Autowired(required = false) KafkaHealthIndicator kafkaHealthIndicator,
        @Autowired(required = false) ZookeeperHealthIndicator zookeeperHealthIndicator,
        SessionRegistry sessionRegistry
    ) {
        this.kafkaService = kafkaService;
        this.directService = directService;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.zookeeperHealthIndicator = zookeeperHealthIndicator;
        this.sessionRegistry = sessionRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing DistributionServiceFactory");
        if (isKafkaAvailable() && kafkaService != null) {
            log.info("📡 Starting with Kafka Distribution Service");
            currentService = kafkaService;
        } else {
            log.info("🔄 Starting with Direct Distribution Service");
            currentService = directService;
        }
        log.info("Current service initialized: {}", currentService.getClass().getSimpleName());
        currentService.startDistribution().subscribe();
    }

    @EventListener
    public void handleInfrastructureStatusChange(InfrastructureStatusChangeEvent event) {
        log.info("Infrastructure status change detected - Kafka: {}, Zookeeper: {}", 
            event.isKafkaAvailable(), event.isZookeeperAvailable());
            
        log.info("Current service before status change: {}", 
            currentService != null ? currentService.getClass().getSimpleName() : "null");
            
        if (event.isInfrastructureAvailable() && kafkaService != null) {
            switchToKafkaService();
        } else {
            switchToDirectService();
        }
    }

    private void switchToKafkaService() {
        if (currentService != null) {
            log.info("Switching to Kafka service...");
            // 현재 활성 세션 정보 저장
            Map<String, Sinks.Many<StandardExchangeData>> activeSinks = currentService.getActiveSinks();
            int activeSessionCount = sessionRegistry.getActiveSessionCount();
            
            log.info("Current active sessions: {}, sinks: {}", 
                activeSessionCount, activeSinks.size());

            currentService.stopDistribution()
                .doOnSuccess(v -> {
                    currentService = kafkaService;
                    // 새 서비스에 세션 정보 복원
                    kafkaService.restoreSinks(activeSinks);
                    currentService.startDistribution().subscribe();
                    log.info("Successfully switched to Kafka service with {} restored sinks", 
                        activeSinks.size());
                })
                .subscribe();
        }
    }
    
    private void switchToDirectService() {
        if (currentService instanceof KafkaDistributionService) {
            log.info("=== Starting Switch to Direct Service ===");
            
            // 1. 먼저 Sink 정보 저장
            KafkaDistributionService kafkaService = (KafkaDistributionService) currentService;
            Map<String, Sinks.Many<StandardExchangeData>> activeSinks = new HashMap<>(kafkaService.clientSinks);
            
            log.info("Current State:");
            log.info("├─ Active Sessions: {}", sessionRegistry.getActiveSessionCount());
            log.info("├─ Active Sinks captured: {}", activeSinks.size());
            
            // 2. 서비스 전환
            Mono.when(
                currentService.stopDistribution(),
                this.kafkaService != null ? this.kafkaService.stopDistribution() : Mono.empty()
            ).doOnSuccess(v -> {
                // 3. Direct 서비스로 전환 및 Sink 복원
                currentService = directService;
                directService.restoreSinks(activeSinks);
                
                // 4. 서비스 시작
                currentService.startDistribution().subscribe();
                
                log.info("Switch completed - Restored sinks: {}", directService.getActiveSinks().size());
            }).subscribe();
        }
    }

    @Scheduled(fixedRate = 10000) // 10초마다 현재 서비스 상태 로깅
    public void logCurrentService() {
        if (currentService != null) {
            log.info("Current Distribution Service: {} (isDistributing: {})", 
                currentService.getClass().getSimpleName(),
                currentService.isDistributing());
        } else {
            log.warn("No distribution service is currently active!");
        }
    }

    private boolean isKafkaAvailable() {
        return kafkaHealthIndicator != null && 
               zookeeperHealthIndicator != null && 
               kafkaHealthIndicator.isAvailable() && 
               zookeeperHealthIndicator.isAvailable();
    }

    public DistributionService getCurrentService() {
        return currentService;
    }
} 