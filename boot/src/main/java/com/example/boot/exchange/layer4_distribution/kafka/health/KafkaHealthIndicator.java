package com.example.boot.exchange.layer4_distribution.kafka.health;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.health.HealthCheckable;
import com.example.boot.exchange.layer4_distribution.common.health.InfrastructureStatus;
import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaHealthIndicator implements HealthCheckable {
    private final KafkaTemplate<String, StandardExchangeData> kafkaTemplate;
    private final LeaderElectionService leaderElectionService;
    private final DistributionStatus distributionStatus;
    private final String topic;

    public KafkaHealthIndicator(
        KafkaTemplate<String, StandardExchangeData> kafkaTemplate,
        LeaderElectionService leaderElectionService,
        DistributionStatus distributionStatus,
        @Value("${spring.kafka.topics.trades}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.leaderElectionService = leaderElectionService;
        this.distributionStatus = distributionStatus;
        this.topic = topic;
    }

    @Override
    public String getServiceName() {
        return "Kafka";
    }

    @Override
    public boolean isAvailable() {
        try {
            // 실제로 Kafka에 연결 시도
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor(topic);
            return true;
        } catch (Exception e) {
            log.error("Kafka availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public InfrastructureStatus checkHealth() {
        String brokers = kafkaTemplate.getProducerFactory()
            .getConfigurationProperties()
            .get("bootstrap.servers")
            .toString();
            
        try {
            // Kafka가 연결되어 있는지 확인
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor(topic);
            log.info("Kafka is available - Connected to: {}", brokers);
            
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("CONNECTED")
                .target(brokers)
                .details(Map.of(
                    "topic", topic,
                    "role", leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER",
                    "distributing", distributionStatus.isDistributing()
                ))
                .lastChecked(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to connect to Kafka: {}", e.getMessage());
            
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("DISCONNECTED")
                .target(brokers)
                .details(Map.of(
                    "error", e.getMessage()
                ))
                .lastChecked(LocalDateTime.now())
                .build();
        }
    }
} 