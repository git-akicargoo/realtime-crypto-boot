package com.example.boot.exchange_layer.layer6_message_broker;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.election.LeaderElector;
import com.example.boot.exchange_layer.layer5_message_handler.MessageHandler;
import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {
    
    private final List<MessageHandler> handlers;
    private final KafkaTemplate<String, NormalizedMessage> kafkaTemplate;
    private final LeaderElector leaderElector;
    
    @Value("${app.kafka.topic.trades}")
    private String tradesTopic;
    
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
                    .flatMap(normalizedMessage -> {
                        CompletableFuture<SendResult<String, NormalizedMessage>> future = 
                            kafkaTemplate.send(tradesTopic, 
                                    normalizedMessage.getSymbol(), 
                                    normalizedMessage)
                            .toCompletableFuture();
                        return Mono.fromFuture(future);
                    })
                    .doOnSuccess(result -> 
                        log.debug("Message published to Kafka: exchange={}, symbol={}", 
                                exchange, 
                                result.getProducerRecord().value().getSymbol())
                    )
                    .doOnError(error -> 
                        log.error("Failed to publish message: exchange={}, error={}", 
                                exchange, 
                                error.getMessage())
                    )
                    .then();
            });
    }
    
    private MessageHandler findHandler(String exchange) {
        return handlers.stream()
            .filter(h -> h.supports(exchange))
            .findFirst()
            .orElse(null);
    }
} 