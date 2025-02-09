package com.example.boot.exchange.websocket;

import java.util.concurrent.TimeUnit;

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
public class KafkaWebSocketManager extends AbstractWebSocketManager {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private volatile boolean isLeader = false;
    
    public KafkaWebSocketManager(ExchangeConfigVO config, WebSocketClient client, KafkaTemplate<String, String> kafkaTemplate) {
        super(config, client);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    public void tryBecomeLeader() {
        try {
            kafkaTemplate.send("exchange.leader", "LEADER_CLAIM").get(5, TimeUnit.SECONDS);
            isLeader = true;
            log.info("Became leader - initializing exchange connections");
            connectToExchanges();
        } catch (Exception e) {
            isLeader = false;
            log.info("Another instance is leader - waiting for data from Kafka");
        }
    }

    @Override
    public void handleExchangeData(String exchange, String data) {
        if (isLeader) {
            log.info("Publishing data from {} to Kafka", exchange);
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
            // TODO: 실제 데이터 처리 로직 구현
        }
    }
} 