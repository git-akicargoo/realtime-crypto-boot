package com.example.boot.exchange.layer4_distribution.common.health;

import org.springframework.stereotype.Component;

@Component
public class DistributionStatus {
    private volatile boolean isDistributing = false;
    
    public void setDistributing(boolean distributing) {
        this.isDistributing = distributing;
    }
    
    public boolean isDistributing() {
        return isDistributing;
    }
} 