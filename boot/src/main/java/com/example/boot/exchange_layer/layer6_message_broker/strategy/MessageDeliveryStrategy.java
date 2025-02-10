package com.example.boot.exchange_layer.layer6_message_broker.strategy;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;

import reactor.core.publisher.Mono;

public interface MessageDeliveryStrategy {
    Mono<Void> deliverMessage(String exchange, NormalizedMessage message);
} 