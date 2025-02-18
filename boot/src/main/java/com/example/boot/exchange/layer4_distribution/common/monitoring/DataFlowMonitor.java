package com.example.boot.exchange.layer4_distribution.common.monitoring;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.health.ZookeeperHealthIndicator;
import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "infrastructure.monitoring.data-flow.enabled", havingValue = "true")
public class DataFlowMonitor {
    private final AtomicLong exchangeDataReceived = new AtomicLong(0);
    private final AtomicLong kafkaMessagesSent = new AtomicLong(0);
    private final AtomicLong kafkaMessagesReceived = new AtomicLong(0);
    private final AtomicLong clientMessagesSent = new AtomicLong(0);
    
    private final LeaderElectionService leaderElectionService;
    private final DistributionStatus distributionStatus;
    private final KafkaHealthIndicator healthIndicator;
    private final ZookeeperHealthIndicator zookeeperHealthIndicator;
    
    @Value("${infrastructure.monitoring.data-flow.logging.interval:10000}")
    private long monitoringInterval;
    
    public DataFlowMonitor(
        LeaderElectionService leaderElectionService,
        DistributionStatus distributionStatus,
        @Autowired(required = false) KafkaHealthIndicator healthIndicator,
        @Autowired(required = false) ZookeeperHealthIndicator zookeeperHealthIndicator
    ) {
        this.leaderElectionService = leaderElectionService;
        this.distributionStatus = distributionStatus;
        this.healthIndicator = healthIndicator;
        this.zookeeperHealthIndicator = zookeeperHealthIndicator;
    }
    
    @Scheduled(fixedRateString = "${infrastructure.monitoring.data-flow.logging.interval:10000}")
    public void logMetrics() {
        try {
            if (!distributionStatus.isDistributing()) {
                log.debug("Distribution is not active");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n📊 System Status\n");
            
            // 현재 서비스 타입을 확인하여 모드 결정
            boolean isKafkaMode = healthIndicator != null && 
                                zookeeperHealthIndicator != null && 
                                healthIndicator.isAvailable() && 
                                zookeeperHealthIndicator.isAvailable();
            
            // Direct 모드일 때
            if (!isKafkaMode) {
                long received = exchangeDataReceived.get();
                long sent = clientMessagesSent.get();
                
                sb.append("├─ Mode: DIRECT\n");
                sb.append(String.format("├─ Exchange Data Received: %d\n", received));
                sb.append(String.format("├─ Clients Connected: %d\n", sent > 0 ? 1 : 0));
                sb.append(String.format("└─ Client Messages Sent: %d", sent));
            } 
            // Kafka 모드일 때
            else {
                String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
                sb.append("├─ Mode: KAFKA\n");
                sb.append(String.format("├─ Role: %s\n", role));
                sb.append(String.format("├─ Kafka Messages: Sent=%d, Received=%d\n", 
                    kafkaMessagesSent.get(), kafkaMessagesReceived.get()));
                sb.append(String.format("└─ Client Messages Sent: %d", clientMessagesSent.get()));
            }
            
            log.info(sb.toString());
            
        } catch (Exception e) {
            log.error("Error during metrics logging", e);
        }
    }

    public void incrementExchangeData() {
        exchangeDataReceived.incrementAndGet();
    }
    
    public void incrementKafkaSent() {
        kafkaMessagesSent.incrementAndGet();
    }
    
    public void incrementKafkaReceived() {
        kafkaMessagesReceived.incrementAndGet();
    }
    
    public void incrementClientSent() {
        clientMessagesSent.incrementAndGet();
    }
} 