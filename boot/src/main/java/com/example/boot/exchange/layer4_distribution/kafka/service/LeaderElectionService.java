package com.example.boot.exchange.layer4_distribution.kafka.service;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class LeaderElectionService {
    
    private final LeaderLatch leaderLatch;
    private volatile boolean isLeader = false;
    
    public LeaderElectionService(CuratorFramework client) {
        this.leaderLatch = new LeaderLatch(client, "/leader");
        
        this.leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                isLeader = true;
                log.info("👑 This instance is now the LEADER\n" +
                        "└─ Starting to process exchange data");
            }
            
            @Override
            public void notLeader() {
                isLeader = false;
                log.info("⚡ This instance is now a FOLLOWER\n" +
                        "└─ Waiting for data from leader");
            }
        });
    }
    
    @PostConstruct
    public void start() throws Exception {
        leaderLatch.start();
        log.info("Leader election service started");
    }
    
    @PreDestroy
    public void stop() throws Exception {
        leaderLatch.close();
        log.info("Leader election service stopped");
    }
    
    public boolean isLeader() {
        return isLeader;
    }
} 