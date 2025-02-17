package com.example.boot.exchange.layer4_distribution.kafka.health;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer4_distribution.common.health.HealthCheckable;
import com.example.boot.exchange.layer4_distribution.common.health.InfrastructureStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class ZookeeperHealthIndicator implements HealthCheckable {
    private final CuratorFramework client;
    private final String connectString;
    private ZooKeeper zooKeeper;  // 재사용 가능한 ZooKeeper 인스턴스
    
    public ZookeeperHealthIndicator(
        @Autowired(required = false) CuratorFramework client,
        @Value("${zookeeper.connect-string}") String connectString
    ) {
        this.client = client;
        this.connectString = connectString;
        initializeZooKeeper();  // ZooKeeper 초기화
    }
    
    private void initializeZooKeeper() {
        if (client == null) {
            try {
                zooKeeper = new ZooKeeper(connectString, 3000, event -> {
                    log.debug("ZooKeeper event: {}", event.getType());
                });
                log.info("Created direct ZooKeeper connection to {}", connectString);
            } catch (IOException e) {
                log.error("Failed to initialize ZooKeeper connection: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            if (client != null) {
                boolean connected = client.getZookeeperClient().isConnected();
                log.debug("Checking Zookeeper availability via client: {}", connected);
                return connected;
            }
            
            if (zooKeeper != null) {
                boolean connected = zooKeeper.getState().isConnected();
                log.debug("Checking Zookeeper availability via direct connection: {}", connected);
                if (!connected) {
                    // 연결이 끊어졌다면 재연결 시도
                    initializeZooKeeper();
                }
                return connected;
            }
            
            log.warn("No Zookeeper client available");
            return false;
        } catch (Exception e) {
            log.debug("Zookeeper check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public InfrastructureStatus checkHealth() {
        try {
            if (client != null && client.getZookeeperClient().isConnected()) {
                return InfrastructureStatus.builder()
                    .serviceName(getServiceName())
                    .status("CONNECTED")
                    .target(connectString)
                    .details(Map.of(
                        "namespace", client.getNamespace(),
                        "sessionId", String.valueOf(client.getZookeeperClient().getZooKeeper().getSessionId())
                    ))
                    .lastChecked(LocalDateTime.now())
                    .build();
            }
            
            if (zooKeeper != null && zooKeeper.getState().isConnected()) {
                return InfrastructureStatus.builder()
                    .serviceName(getServiceName())
                    .status("CONNECTED")
                    .target(connectString)
                    .details(Map.of(
                        "sessionId", String.valueOf(zooKeeper.getSessionId()),
                        "state", zooKeeper.getState().toString()
                    ))
                    .lastChecked(LocalDateTime.now())
                    .build();
            }
            
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("DISCONNECTED")
                .target(connectString)
                .details(Map.of("error", "No active Zookeeper connection"))
                .lastChecked(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            return InfrastructureStatus.builder()
                .serviceName(getServiceName())
                .status("DISCONNECTED")
                .target(connectString)
                .details(Map.of("error", e.getMessage()))
                .lastChecked(LocalDateTime.now())
                .build();
        }
    }

    @Override
    public String getServiceName() {
        return "Zookeeper";
    }
} 