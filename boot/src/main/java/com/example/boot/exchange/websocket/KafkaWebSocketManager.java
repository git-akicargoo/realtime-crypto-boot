package com.example.boot.exchange.websocket;

import java.util.Collection;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.config.ExchangeConfigVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaWebSocketManager extends AbstractWebSocketManager implements ConsumerRebalanceListener {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private volatile boolean isLeader = false;
    
    public KafkaWebSocketManager(ExchangeConfigVO config, WebSocketClient client, KafkaTemplate<String, String> kafkaTemplate) {
        super(config, client);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        log.info("Partitions assigned: {}", partitions);
        boolean wasLeader = isLeader;
        isLeader = partitions.stream()
            .anyMatch(p -> p.topic().equals("exchange.data") && p.partition() == 0);
            
        log.info("Leader status changed: {} -> {}", wasLeader, isLeader);
        
        if (!wasLeader && isLeader) {
            log.info("Became leader - initializing WebSocket connections");
            init();
        } else if (wasLeader && !isLeader) {
            log.info("Lost leadership - closing WebSocket connections");
            closeConnections();
        }
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        log.info("Partitions revoked: {}", partitions);
        if (isLeader) {
            log.info("Leadership revoked - closing WebSocket connections");
            closeConnections();
            isLeader = false;
        }
    }

    private void closeConnections() {
        sessions.forEach((exchange, session) -> {
            try {
                session.close().subscribe();
            } catch (Exception e) {
                log.error("Error closing {} session", exchange, e);
            }
        });
        sessions.clear();
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    public void tryBecomeLeader() {
        // 리더십은 Kafka Consumer Group이 관리
        log.info("Leadership managed by Kafka Consumer Group");
    }

    @Override
    public void handleExchangeData(String exchange, String data) {
        if (isLeader) {
            log.info("Leader publishing data from {} to Kafka", exchange);
            kafkaTemplate.send("exchange.data", data)
                .exceptionally(e -> {
                    log.error("Failed to publish to Kafka", e);
                    return null;
                });
        }
    }

    @KafkaListener(topics = "exchange.data", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeExchangeData(String data) {
        if (!isLeader) {
            log.info("Follower processing data from Kafka: {}", data);
            // TODO: 클라이언트로 데이터 전송 로직 구현
        }
    }
} 