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

@Component
@Slf4j
public class DistributionServiceFactory {
    private final DirectDistributionService directService;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final ZookeeperHealthIndicator zookeeperHealthIndicator;
    private final KafkaDistributionService kafkaService;
    private DistributionService currentService;

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
            
        // 현재 서비스 상태 로깅
        log.info("Current service before status change: {}", 
            currentService != null ? currentService.getClass().getSimpleName() : "null");
        
        // 디버깅을 위한 조건 로깅 추가
        log.info("Checking conditions - kafkaService != null: {}, event.isInfrastructureAvailable: {}, currentService instanceof KafkaDistributionService: {}", 
            kafkaService != null,
            event.isInfrastructureAvailable(),
            currentService instanceof KafkaDistributionService);

        if (event.isInfrastructureAvailable()) {
            if (!(currentService instanceof KafkaDistributionService)) {
                log.info("📡 Switching to Kafka Distribution Service");
                switchToKafka();
            }
        } else {
            if (currentService instanceof KafkaDistributionService) {
                log.info("🔄 Switching to Direct Distribution Service");
                switchToDirect();
            }
        }
    }

    private synchronized void switchToKafka() {
        try {
            log.info("Starting switch to Kafka service...");
            if (currentService != null) {
                String previousService = currentService.getClass().getSimpleName();
                log.info("Stopping current service: {}", previousService);
                
                currentService.stopDistribution()
                    .doOnSuccess(v -> {
                        log.info("Successfully stopped {}", previousService);
                        currentService = kafkaService;
                        log.info("Starting Kafka service...");
                        currentService.startDistribution()
                            .doOnSubscribe(s -> log.info("Kafka service subscription started"))
                            .subscribe(
                                data -> log.debug("Kafka service processing data: {}", data),
                                error -> {
                                    log.error("Error in Kafka service", error);
                                    switchToDirect();
                                },
                                () -> log.info("Kafka service subscription completed")
                            );
                        log.info("Current service is now: {}", currentService.getClass().getSimpleName());
                    })
                    .doOnError(error -> {
                        log.error("Error during service switch", error);
                        switchToDirect();
                    })
                    .subscribe();
            } else {
                currentService = kafkaService;
                currentService.startDistribution().subscribe();
                log.info("Started Kafka service directly");
            }
        } catch (Exception e) {
            log.error("Unexpected error during switch to Kafka", e);
            switchToDirect();
        }
    }

    private synchronized void switchToDirect() {
        log.info("Starting switch to Direct service...");
        if (currentService != null) {
            String previousService = currentService.getClass().getSimpleName();
            log.info("Stopping current service: {}", previousService);
            
            currentService.stopDistribution()
                .doOnSuccess(v -> {
                    log.info("Successfully stopped {}", previousService);
                    currentService = directService;
                    log.info("Starting Direct service...");
                    currentService.startDistribution()
                        .doOnSubscribe(s -> log.info("Direct service subscription started"))
                        .subscribe(
                            data -> log.debug("Direct service processing data: {}", data),
                            error -> log.error("Error in Direct service", error),
                            () -> log.info("Direct service completed")
                        );
                    log.info("Current service is now: {}", currentService.getClass().getSimpleName());
                })
                .doOnError(error -> log.error("Error during service switch", error))
                .subscribe();
        } else {
            currentService = directService;
            currentService.startDistribution().subscribe();
            log.info("Direct service started as fallback");
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