package com.example.boot.exchange.layer4_distribution.kafka.health;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.health.HealthCheckable;
import com.example.boot.exchange.layer4_distribution.common.health.InfrastructureStatus;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class ZookeeperHealthIndicator implements HealthCheckable {
    private final String connectString;
    private volatile ZooKeeper zooKeeper;
    private final Object lock = new Object();
    
    public ZookeeperHealthIndicator(@Value("${zookeeper.connect-string}") String connectString) {
        this.connectString = connectString;
    }
    
    @Override
    public InfrastructureStatus checkHealth() {
        synchronized (lock) {
            try {
                if (zooKeeper == null || !zooKeeper.getState().isAlive()) {
                    closeQuietly();
                    zooKeeper = new ZooKeeper(connectString, 3000, event -> {});
                }
                
                ZooKeeper.States state = zooKeeper.getState();
                boolean isConnected = state.isConnected();
                
                return InfrastructureStatus.builder()
                    .serviceName("Zookeeper")
                    .status(isConnected ? "CONNECTED" : "DISCONNECTED")
                    .target(connectString)
                    .details(Map.of(
                        "state", state.toString(),
                        "sessionId", zooKeeper.getSessionId()
                    ))
                    .lastChecked(LocalDateTime.now())
                    .build();
                    
            } catch (Exception e) {
                closeQuietly();
                return InfrastructureStatus.builder()
                    .serviceName("Zookeeper")
                    .status("DISCONNECTED")
                    .target(connectString)
                    .details(Map.of("error", e.getMessage()))
                    .lastChecked(LocalDateTime.now())
                    .build();
            }
        }
    }
    
    private void closeQuietly() {
        if (zooKeeper != null) {
            try {
                zooKeeper.close();
            } catch (Exception e) {
                log.warn("Error closing ZooKeeper connection", e);
            }
            zooKeeper = null;
        }
    }
    
    @PreDestroy
    public void destroy() {
        closeQuietly();
    }
    
    @Override
    public boolean isAvailable() {
        InfrastructureStatus status = checkHealth();
        return "CONNECTED".equals(status.getStatus());
    }
    
    @Override
    public String getServiceName() {
        return "Zookeeper";
    }
} 