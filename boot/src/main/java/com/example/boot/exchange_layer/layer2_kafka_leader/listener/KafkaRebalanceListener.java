package com.example.boot.exchange_layer.layer2_kafka_leader.listener;

import java.util.Collection;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.election.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaRebalanceListener implements ConsumerRebalanceListener {
    
    private final LeaderElector leaderElector;
    private static final String LEADER_PARTITION = "exchange.data";
    private static final int LEADER_PARTITION_NUMBER = 0;
    
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        boolean isLeader = partitions.stream()
            .anyMatch(p -> p.topic().equals(LEADER_PARTITION) && 
                         p.partition() == LEADER_PARTITION_NUMBER);
            
        if (isLeader) {
            leaderElector.onLeaderElected()
                .subscribe(
                    null,
                    error -> log.error("Error during leader election: ", error),
                    () -> log.info("Leader election completed")
                );
        }
    }
    
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        leaderElector.isLeader()
            .filter(Boolean::booleanValue)
            .flatMap(__ -> leaderElector.onLeaderRevoked())
            .subscribe(
                null,
                error -> log.error("Error during leader revocation: ", error),
                () -> log.info("Leader revocation completed")
            );
    }
} 