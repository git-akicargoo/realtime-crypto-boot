package com.example.boot.exchange_layer.layer2_kafka_leader.election;

import reactor.core.publisher.Mono;

public interface LeaderElector {
    Mono<Boolean> isLeader();
    Mono<Void> onLeaderElected();
    Mono<Void> onLeaderRevoked();
} 