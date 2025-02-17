package com.example.boot.exchange.layer4_distribution.kafka.config;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class ZookeeperConfig {
    
    @Value("${zookeeper.connect-string}")
    private String connectString;
    
    private final LeaderElectionService leaderElectionService;
    
    public ZookeeperConfig(LeaderElectionService leaderElectionService) {
        this.leaderElectionService = leaderElectionService;
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public CuratorFramework curatorFramework() {
        return createClient();
    }
    
    @EventListener
    public void handleInfrastructureStatusChange(InfrastructureStatusChangeEvent event) {
        if (event.isZookeeperAvailable()) {
            log.info("Zookeeper available, creating new client");
            CuratorFramework newClient = createClient();
            if (newClient != null) {
                log.info("Successfully created new Zookeeper client");
                leaderElectionService.setClient(newClient);  // 직접 클라이언트 설정
            }
        }
    }
    
    private CuratorFramework createClient() {
        try {
            log.info("Initializing Zookeeper client for: {}", connectString);
            
            CuratorFramework newClient = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new RetryNTimes(5, 1000))
                .namespace("exchange")
                .build();
                
            newClient.start();
            
            if (newClient.blockUntilConnected(5, TimeUnit.SECONDS)) {
                log.info("🟢 Successfully connected to Zookeeper at {}", connectString);
                return newClient;
            } else {
                log.warn("Failed to connect to Zookeeper at {}", connectString);
                newClient.close();
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to start Zookeeper client: {}", e.getMessage());
            return null;
        }
    }
} 