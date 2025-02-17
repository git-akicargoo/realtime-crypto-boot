package com.example.boot.exchange.layer4_distribution.kafka.health;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.health.HealthCheckable;
import com.example.boot.exchange.layer4_distribution.common.health.InfrastructureStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class ZookeeperHealthIndicator implements HealthCheckable {
    private final CuratorFramework client;
    private volatile boolean isZookeeperAvailable = false;

    public ZookeeperHealthIndicator(CuratorFramework client) {
        this.client = client;
    }

    @Override
    public String getServiceName() {
        return "Zookeeper";
    }

    @Override
    public boolean isAvailable() {
        return isZookeeperAvailable;
    }

    @Override
    public InfrastructureStatus checkHealth() {
        String connectString = client.getZookeeperClient().getCurrentConnectionString();
        
        try {
            if (client.getZookeeperClient().isConnected()) {
                isZookeeperAvailable = true;
                
                return InfrastructureStatus.builder()
                    .serviceName(getServiceName())
                    .status("CONNECTED")
                    .target(connectString)
                    .details(Map.of(
                        "namespace", client.getNamespace(),
                        "sessionId", client.getZookeeperClient().getZooKeeper().getSessionId()
                    ))
                    .lastChecked(LocalDateTime.now())
                    .build();
            }
        } catch (Exception e) {
            isZookeeperAvailable = false;
            
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("DISCONNECTED")
                .target(connectString)
                .details(Map.of(
                    "error", e.getMessage()
                ))
                .lastChecked(LocalDateTime.now())
                .build();
        }
        
        return InfrastructureStatus.builder()
            .serviceName(getServiceName())
            .status("DISCONNECTED")
            .target(connectString)
            .details(Map.of(
                "error", "Not connected to Zookeeper"
            ))
            .lastChecked(LocalDateTime.now())
            .build();
    }
} 