package com.example.boot.exchange_layer.layer6_message_broker.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaMessageDelivery implements MessageDeliveryStrategy {
    
    private final KafkaTemplate<String, NormalizedMessage> kafkaTemplate;
    
    @Value("${app.kafka.topic.trades}")
    private String tradesTopic;
    
    @Override
    public Mono<Void> deliverMessage(String exchange, NormalizedMessage message) {
        return Mono.fromFuture(() -> 
            kafkaTemplate.send(tradesTopic, message.getSymbol(), message)
                .toCompletableFuture()
        )
        .doOnSuccess(result -> 
            log.debug("Message published to Kafka: exchange={}, symbol={}", 
                    exchange, message.getSymbol())
        )
        .doOnError(error -> 
            log.error("Failed to publish message: exchange={}, error={}", 
                    exchange, error.getMessage())
        )
        .then();
    }
} 