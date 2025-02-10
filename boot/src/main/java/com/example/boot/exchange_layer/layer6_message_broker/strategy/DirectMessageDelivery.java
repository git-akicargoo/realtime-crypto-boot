package com.example.boot.exchange_layer.layer6_message_broker.strategy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;
import com.example.boot.exchange_layer.layer7_client_gateway.websocket.ClientSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class DirectMessageDelivery implements MessageDeliveryStrategy {
    
    private final ClientSessionManager sessionManager;
    
    @Override
    public Mono<Void> deliverMessage(String exchange, NormalizedMessage message) {
        return Mono.fromRunnable(() -> {
            sessionManager.broadcastMessage(message);
            log.debug("Message directly delivered: exchange={}, symbol={}", 
                    exchange, message.getSymbol());
        });
    }
} 