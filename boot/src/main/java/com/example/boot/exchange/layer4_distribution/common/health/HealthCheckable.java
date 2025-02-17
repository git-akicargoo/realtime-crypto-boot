package com.example.boot.exchange.layer4_distribution.common.health;

public interface HealthCheckable {
    String getServiceName();
    InfrastructureStatus checkHealth();
    boolean isAvailable();
} 