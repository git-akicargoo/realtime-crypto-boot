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
    public void logDataFlowMetrics() {
        try {
            // Kafka가 비활성화된 경우
            if (healthIndicator == null || zookeeperHealthIndicator == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n📊 System Status\n");
                sb.append("├─ Mode: Direct (Kafka disabled)\n");
                sb.append(String.format("├─ Distributing: %s\n", distributionStatus.isDistributing()));
                sb.append(String.format("└─ Client Messages Sent: %d", clientMessagesSent.get()));
                log.info(sb.toString());
                return;
            }

            // 헬스체크 상태 확인
            boolean isKafkaAvailable = healthIndicator.isAvailable();
            boolean isZookeeperAvailable = zookeeperHealthIndicator.isAvailable();
            
            StringBuilder sb = new StringBuilder();
            sb.append("\n📊 System Status\n");
            
            if (!isKafkaAvailable || !isZookeeperAvailable) {
                sb.append("├─ Kafka: ").append(isKafkaAvailable ? "🟢 CONNECTED" : "🔴 DISCONNECTED").append("\n");
                sb.append("└─ Zookeeper: ").append(isZookeeperAvailable ? "🟢 CONNECTED" : "🔴 DISCONNECTED");
                log.info(sb.toString());
                return;
            }

            // Kafka와 Zookeeper가 모두 연결된 경우에만 상세 메트릭 표시
            String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
            sb.append(String.format("├─ Role: %s\n", role));
            sb.append(String.format("├─ Distributing: %s\n", distributionStatus.isDistributing()));
            sb.append(String.format("├─ Kafka Messages Received: %d\n", kafkaMessagesReceived.get()));
            sb.append(String.format("└─ Client Messages Sent: %d", clientMessagesSent.get()));
            
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