package com.example.boot.exchange.layer4_distribution.common.monitoring;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
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
    
    public DataFlowMonitor(
        LeaderElectionService leaderElectionService,
        DistributionStatus distributionStatus
    ) {
        this.leaderElectionService = leaderElectionService;
        this.distributionStatus = distributionStatus;
    }
    
    @Scheduled(fixedRateString = "${infrastructure.monitoring.data-flow.logging.interval:5000}")
    public void logDataFlowMetrics() {
        String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n📊 Data Flow Metrics\n");
        sb.append(String.format("├─ Role: %s\n", role));
        sb.append(String.format("├─ Distributing: %s\n", distributionStatus.isDistributing()));
        
        if (leaderElectionService.isLeader()) {
            sb.append(String.format("├─ Exchange Data Received: %d\n", exchangeDataReceived.get()));
            sb.append(String.format("├─ Kafka Messages Sent: %d\n", kafkaMessagesSent.get()));
        }
        
        sb.append(String.format("├─ Kafka Messages Received: %d\n", kafkaMessagesReceived.get()));
        sb.append(String.format("└─ Client Messages Sent: %d\n", clientMessagesSent.get()));
        
        // 초당 처리량 계산
        sb.append("\n📈 Processing Rate (per second)\n");
        if (leaderElectionService.isLeader()) {
            sb.append(String.format("├─ Exchange Data: %.2f\n", exchangeDataReceived.get() / 5.0));
            sb.append(String.format("├─ Kafka Sent: %.2f\n", kafkaMessagesSent.get() / 5.0));
        }
        sb.append(String.format("├─ Kafka Received: %.2f\n", kafkaMessagesReceived.get() / 5.0));
        sb.append(String.format("└─ Client Sent: %.2f\n", clientMessagesSent.get() / 5.0));
        
        log.info(sb.toString());
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