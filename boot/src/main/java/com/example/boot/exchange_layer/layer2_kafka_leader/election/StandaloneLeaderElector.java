package com.example.boot.exchange_layer.layer2_kafka_leader.election;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer2_kafka_leader.event.LeaderElectedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class StandaloneLeaderElector implements LeaderElector {
    
    private final ApplicationEventPublisher eventPublisher;
    
    @Override
    public Mono<Boolean> isLeader() {
        return Mono.just(true);  // 항상 리더
    }
    
    @Override
    public Mono<Void> onLeaderElected() {
        log.info("Standalone mode - always leader");
        return Mono.fromRunnable(() -> 
            eventPublisher.publishEvent(new LeaderElectedEvent())
        );
    }
    
    @Override
    public Mono<Void> onLeaderRevoked() {
        return Mono.empty();  // 단독 실행에서는 리더 권한이 취소되지 않음
    }
} 