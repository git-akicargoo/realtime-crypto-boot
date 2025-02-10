package com.example.boot.exchange_layer.layer6_message_broker;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.election.LeaderElector;
import com.example.boot.exchange_layer.layer5_message_handler.MessageHandler;
import com.example.boot.exchange_layer.layer6_message_broker.strategy.MessageDeliveryStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {
    
    private final List<MessageHandler> handlers;
    private final LeaderElector leaderElector;
    private final MessageDeliveryStrategy messageDeliveryStrategy;
    
    public Mono<Void> publish(String exchange, String message) {
        return leaderElector.isLeader()
            .flatMap(isLeader -> {
                if (!isLeader) {
                    return Mono.empty();
                }
                
                MessageHandler handler = findHandler(exchange);
                if (handler == null) {
                    log.warn("No handler found for exchange: {}", exchange);
                    return Mono.empty();
                }
                
                return handler.handleMessage(message)
                    .flatMap(normalizedMessage -> 
                        messageDeliveryStrategy.deliverMessage(exchange, normalizedMessage)
                    )
                    .doOnError(error -> 
                        log.error("Failed to process message: exchange={}, error={}", 
                                exchange, 
                                error.getMessage())
                    );
            });
    }
    
    private MessageHandler findHandler(String exchange) {
        return handlers.stream()
            .filter(h -> h.supports(exchange))
            .findFirst()
            .orElse(null);
    }
} 