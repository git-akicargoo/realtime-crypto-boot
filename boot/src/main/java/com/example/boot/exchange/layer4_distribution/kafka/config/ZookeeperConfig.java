package com.example.boot.exchange.layer4_distribution.kafka.config;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class ZookeeperConfig {
    
    @Value("${zookeeper.connect-string}")
    private String connectString;
    
    private CuratorFramework client;
    
    @Bean
    public CuratorFramework curatorFramework() {
        return createClient();
    }
    
    public synchronized CuratorFramework createClient() {
        if (client != null && client.getState() == CuratorFrameworkState.STARTED) {
            return client;
        }
        
        log.info("Initializing Zookeeper client for: {}", connectString);
        
        CuratorFramework newClient = CuratorFrameworkFactory.builder()
            .connectString(connectString)
            .sessionTimeoutMs(5000)
            .connectionTimeoutMs(3000)
            .retryPolicy(new RetryNTimes(5, 1000))
            .namespace("exchange")
            .build();
            
        try {
            newClient.start();
            if (!newClient.blockUntilConnected(10, TimeUnit.SECONDS)) {
                log.error("Failed to connect to Zookeeper at {}", connectString);
                newClient.close();
                return null;
            }
            log.info("🟢 Successfully connected to Zookeeper at {}", connectString);
            this.client = newClient;
            return newClient;
        } catch (Exception e) {
            log.error("Failed to start Zookeeper client: {}", e.getMessage());
            if (newClient != null) {
                newClient.close();
            }
            return null;
        }
    }
    
    @EventListener
    public void handleInfrastructureStatusChange(InfrastructureStatusChangeEvent event) {
        if (event.isZookeeperAvailable() && client == null) {
            log.info("Zookeeper available, creating new client");
            createClient();
        }
    }
} 