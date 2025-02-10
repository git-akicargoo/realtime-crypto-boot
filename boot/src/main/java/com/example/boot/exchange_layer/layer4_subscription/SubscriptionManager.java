package com.example.boot.exchange_layer.layer4_subscription;

import com.example.boot.exchange_layer.layer4_subscription.model.SubscriptionRequest;

import reactor.core.publisher.Mono;

public interface SubscriptionManager {
    Mono<Void> subscribe(SubscriptionRequest request);
    Mono<Void> unsubscribe(SubscriptionRequest request);
} 