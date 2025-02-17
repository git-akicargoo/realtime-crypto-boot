package com.example.boot.exchange.layer4_distribution.kafka.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class ZookeeperConfig {
    
    @Value("${zookeeper.connect-string}")
    private String connectString;
    
    @Bean
    public CuratorFramework curatorFramework() {
        CuratorFramework client = CuratorFrameworkFactory.builder()
            .connectString(connectString)
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .namespace("exchange")
            .build();
            
        client.start();
        log.info("Zookeeper client started with connection string: {}", connectString);
        return client;
    }
} 