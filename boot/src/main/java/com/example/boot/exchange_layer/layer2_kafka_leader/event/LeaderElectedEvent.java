package com.example.boot.exchange_layer.layer2_kafka_leader.event;

import org.springframework.context.ApplicationEvent;

public class LeaderElectedEvent extends ApplicationEvent {
    public LeaderElectedEvent() {
        super("LeaderElected");
    }
} 