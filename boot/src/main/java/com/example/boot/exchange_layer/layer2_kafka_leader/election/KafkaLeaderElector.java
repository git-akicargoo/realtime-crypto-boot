package com.example.boot.exchange_layer.layer2_kafka_leader.election;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.event.LeaderElectedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaLeaderElector implements LeaderElector {
    
    private final ApplicationEventPublisher eventPublisher;
    private volatile boolean isLeader = false;
    
    @Override
    public Mono<Boolean> isLeader() {
        return Mono.just(isLeader);
    }
    
    @Override
    public Mono<Void> onLeaderElected() {
        log.info("Leader elected - initializing connections");
        isLeader = true;
        return Mono.fromRunnable(() -> 
            eventPublisher.publishEvent(new LeaderElectedEvent())
        );
    }
    
    @Override
    public Mono<Void> onLeaderRevoked() {
        log.info("Leadership revoked - closing connections");
        isLeader = false;
        return Mono.empty(); // 필요한 경우 리더 해제 이벤트도 추가 가능
    }
} 