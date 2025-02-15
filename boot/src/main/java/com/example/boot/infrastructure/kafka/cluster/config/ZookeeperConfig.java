package com.example.boot.infrastructure.kafka.cluster.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class ZookeeperConfig {
    
    @Value("${zookeeper.connect-string}")
    private String connectString;
    
    @Bean
    public CuratorFramework curatorFramework() {
        var retryPolicy = new ExponentialBackoffRetry(1000, 3);
        var client = CuratorFrameworkFactory.builder()
            .connectString(connectString)
            .retryPolicy(retryPolicy)
            .build();
        client.start();
        return client;
    }
} 