package com.example.boot.exchange.layer4_distribution.common.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.direct.service.DirectDistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.health.ZookeeperHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.service.KafkaDistributionService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class DistributionServiceFactory {
    private final KafkaDistributionService kafkaService;
    private final DirectDistributionService directService;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final ZookeeperHealthIndicator zookeeperHealthIndicator;
    private volatile DistributionService currentService;

    public DistributionServiceFactory(
        @Autowired(required = false) KafkaDistributionService kafkaService,
        DirectDistributionService directService,
        @Autowired(required = false) KafkaHealthIndicator kafkaHealthIndicator,
        @Autowired(required = false) ZookeeperHealthIndicator zookeeperHealthIndicator
    ) {
        this.kafkaService = kafkaService;
        this.directService = directService;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.zookeeperHealthIndicator = zookeeperHealthIndicator;
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
            log.info("Stopping current service: {}", currentService.getClass().getSimpleName());
            currentService.stopDistribution()
                .doOnSuccess(v -> {
                    currentService = kafkaService;
                    currentService.startDistribution().subscribe();
                    log.info("Successfully switched to Kafka service");
                })
                .subscribe();
        }
    }
    
    private void switchToDirectService() {
        if (currentService instanceof KafkaDistributionService) {
            log.info("Switching from Kafka to Direct service");
            // 먼저 Kafka 서비스 중지
            Mono.when(
                currentService.stopDistribution(),
                kafkaService != null ? kafkaService.stopDistribution() : Mono.empty()
            ).doOnSuccess(v -> {
                log.info("Successfully stopped Kafka service");
                currentService = directService;
                currentService.startDistribution().subscribe();
                log.info("Successfully started Direct service");
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