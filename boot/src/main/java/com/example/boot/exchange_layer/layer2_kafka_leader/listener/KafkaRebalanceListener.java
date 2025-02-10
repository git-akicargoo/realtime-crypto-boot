package com.example.boot.exchange_layer.layer2_kafka_leader.listener;

import java.util.Collection;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.election.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaRebalanceListener implements ConsumerRebalanceListener {
    
    private final LeaderElector leaderElector;
    @Value("${app.kafka.topic.leader}")
    private String leaderTopic;
    @Value("${app.kafka.leader.partition}")
    private int leaderPartition;
    
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        boolean isLeader = partitions.stream()
            .anyMatch(p -> p.topic().equals(leaderTopic) && 
                         p.partition() == leaderPartition);
            
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