package com.example.boot.exchange.layer4_distribution.kafka.health;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.health.HealthCheckable;
import com.example.boot.exchange.layer4_distribution.common.health.InfrastructureStatus;
import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator implements HealthCheckable {
    private final KafkaTemplate<String, StandardExchangeData> kafkaTemplate;
    private final LeaderElectionService leaderElectionService;
    private final DistributionStatus distributionStatus;
    private final String topic;
    private final ZookeeperHealthIndicator zookeeperHealthIndicator;
    private final String bootstrapServers;
    private final ScheduledLogger scheduledLogger;

    public KafkaHealthIndicator(
        @Autowired(required = false) KafkaTemplate<String, StandardExchangeData> kafkaTemplate,
        LeaderElectionService leaderElectionService,
        DistributionStatus distributionStatus,
        @Value("${spring.kafka.topics.trades}") String topic,
        ZookeeperHealthIndicator zookeeperHealthIndicator,
        @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
        ScheduledLogger scheduledLogger
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.leaderElectionService = leaderElectionService;
        this.distributionStatus = distributionStatus;
        this.topic = topic;
        this.zookeeperHealthIndicator = zookeeperHealthIndicator;
        this.bootstrapServers = bootstrapServers;
        this.scheduledLogger = scheduledLogger;
    }

    @Override
    public boolean isAvailable() {
        // Zookeeper가 먼저 연결되어 있어야 함
        if (!zookeeperHealthIndicator.isAvailable()) {
            log.debug("Zookeeper is not available, skipping Kafka check");
            return false;
        }

        try {
            if (kafkaTemplate == null) {
                log.debug("KafkaTemplate is null, checking direct connection to {}", bootstrapServers);
                Properties props = new Properties();
                props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, "3000");
                props.put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000");
                
                try (AdminClient adminClient = AdminClient.create(props)) {
                    adminClient.listTopics().names().get(3, TimeUnit.SECONDS);
                    scheduledLogger.scheduleLog(log, "Successfully connected to Kafka at {}", bootstrapServers);
                    return true;
                }
            }
            
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor(topic);
            scheduledLogger.scheduleLog(log, "Kafka is available via KafkaTemplate at {}", bootstrapServers);
            return true;
        } catch (Exception e) {
            log.debug("Kafka is not available at {}: {}", bootstrapServers, e.getMessage());
            return false;
        }
    }

    @Override
    public InfrastructureStatus checkHealth() {
        try {
            boolean available = isAvailable();
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status(available ? "CONNECTED" : "DISCONNECTED")
                .target(bootstrapServers)
                .details(Map.of(
                    "topic", topic,
                    "clientType", kafkaTemplate != null ? "KafkaTemplate" : "DirectConnection",
                    "isLeader", leaderElectionService.isLeader(),
                    "isDistributing", distributionStatus.isDistributing()
                ))
                .lastChecked(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("DISCONNECTED")
                .target(bootstrapServers)
                .details(Map.of("error", e.getMessage()))
                .lastChecked(LocalDateTime.now())
                .build();
        }
    }

    @Override
    public String getServiceName() {
        return "Kafka";
    }
} 