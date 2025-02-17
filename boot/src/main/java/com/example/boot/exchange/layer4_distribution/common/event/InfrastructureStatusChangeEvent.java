package com.example.boot.exchange.layer4_distribution.common.event;

import lombok.Getter;

@Getter
public class InfrastructureStatusChangeEvent {
    private final boolean kafkaAvailable;
    private final boolean zookeeperAvailable;

    public InfrastructureStatusChangeEvent(boolean kafkaAvailable, boolean zookeeperAvailable) {
        this.kafkaAvailable = kafkaAvailable;
        this.zookeeperAvailable = zookeeperAvailable;
    }

    public boolean isInfrastructureAvailable() {
        return kafkaAvailable && zookeeperAvailable;
    }
}