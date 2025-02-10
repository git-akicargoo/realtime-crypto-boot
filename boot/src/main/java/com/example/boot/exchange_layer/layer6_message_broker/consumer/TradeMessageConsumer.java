package com.example.boot.exchange_layer.layer6_message_broker.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;
import com.example.boot.exchange_layer.layer7_client_gateway.websocket.ClientSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class TradeMessageConsumer {
    
    private final ClientSessionManager sessionManager;
    
    @KafkaListener(
        topics = "${app.kafka.topic.trades}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(NormalizedMessage message) {
        log.debug("Received trade message: {}", message);
        sessionManager.broadcastMessage(message);
    }
} 