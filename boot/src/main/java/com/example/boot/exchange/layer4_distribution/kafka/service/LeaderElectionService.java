package com.example.boot.exchange.layer4_distribution.kafka.service;

import java.util.UUID;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.event.LeaderElectionEvent;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class LeaderElectionService {
    
    private final ApplicationEventPublisher eventPublisher;
    private final String nodeId;
    private volatile CuratorFramework client;
    private volatile LeaderLatch leaderLatch;
    private volatile boolean isLeader = false;
    private volatile boolean isElectionEnabled = false;
    
    public LeaderElectionService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.nodeId = UUID.randomUUID().toString();
    }
    
    @Autowired(required = false)
    public void setClient(@Nullable CuratorFramework client) {
        this.client = client;
        if (client == null) {
            log.info("No Zookeeper client available - leader election disabled");
            isElectionEnabled = false;
            setLeaderState(false);
        } else {
            log.info("Zookeeper client available - starting leader election");
            isElectionEnabled = true;
            start();  // 클라이언트가 있으면 리더 선출 시작
        }
    }
    
    @EventListener
    public void handleInfrastructureStatusChange(InfrastructureStatusChangeEvent event) {
        if (!event.isInfrastructureAvailable()) {
            log.info("Infrastructure unavailable, stopping leader election");
            stop();
            isElectionEnabled = false;
            setLeaderState(false);
        } else if (client != null) {
            log.info("Infrastructure available and client exists, restarting leader election");
            stop();
            start();
        }
    }
    
    public void start() {
        if (client == null) {
            log.warn("Cannot start leader election - no Zookeeper client available");
            return;
        }
        
        try {
            log.info("Initializing leader election for node {}", nodeId);
            stop();  // 기존 리더 선출 중지
            
            leaderLatch = new LeaderLatch(client, "/leader", nodeId);
            leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    log.info("Node {} became leader", nodeId);
                    setLeaderState(true);
                }
                
                @Override
                public void notLeader() {
                    log.info("Node {} lost leadership", nodeId);
                    setLeaderState(false);
                }
            });
            
            leaderLatch.start();
            isElectionEnabled = true;
            log.info("Leader election started for node {}", nodeId);
            
        } catch (Exception e) {
            log.error("Failed to initialize leader election: {}", e.getMessage());
            isElectionEnabled = false;
            setLeaderState(false);
        }
    }
    
    @PreDestroy
    public void stop() {
        if (leaderLatch != null) {
            try {
                if (leaderLatch.getState() == LeaderLatch.State.STARTED) {
                    leaderLatch.close();
                    log.info("Leader election service stopped");
                }
            } catch (Exception e) {
                log.warn("Failed to stop leader election: {}", e.getMessage());
            } finally {
                leaderLatch = null;
                isElectionEnabled = false;
                setLeaderState(false);
            }
        }
    }
    
    private void setLeaderState(boolean leader) {
        if (this.isLeader != leader) {  // 상태가 변경될 때만
            this.isLeader = leader;
            log.info("👑 Node {} is now {}", nodeId, leader ? "LEADER" : "FOLLOWER");
            eventPublisher.publishEvent(new LeaderElectionEvent(leader, nodeId));
        }
    }
    
    public boolean isLeader() {
        if (!isElectionEnabled) {
            log.debug("Leader election is not enabled");
            return false;
        }
        return isLeader;
    }

    public boolean isElectionEnabled() {
        return isElectionEnabled;
    }
} 