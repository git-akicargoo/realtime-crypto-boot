package com.example.boot.exchange.layer4_distribution.common.health;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InfrastructureStatus {
    private String serviceName;
    private String status;
    private String target;
    private Map<String, Object> details;
    private LocalDateTime lastChecked;
} 