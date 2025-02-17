package com.example.boot.exchange.layer4_distribution.kafka.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer4_distribution.common.event.InfrastructureStatusChangeEvent;
import com.example.boot.exchange.layer4_distribution.common.event.LeaderElectionEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "infrastructure.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class LeaderElectionService {
    
    private final CuratorFramework client;
    private final ApplicationEventPublisher eventPublisher;
    private final String nodeId;
    private LeaderLatch leaderLatch;
    private volatile boolean isLeader = false;
    private volatile boolean isElectionEnabled = false;
    
    public LeaderElectionService(
        @Autowired(required = false) CuratorFramework client, 
        ApplicationEventPublisher eventPublisher
    ) {
        this.client = client;
        this.eventPublisher = eventPublisher;
        this.nodeId = UUID.randomUUID().toString();
    }
    
    @EventListener
    public void handleInfrastructureStatusChange(InfrastructureStatusChangeEvent event) {
        if (event.isZookeeperAvailable()) {
            log.info("Zookeeper reconnected, restarting leader election");
            stop();  // 기존 리더 선출 정리
            start(); // 리더 선출 재시작
        } else {
            log.info("Zookeeper disconnected, stopping leader election");
            stop();
            isElectionEnabled = false;
            setLeaderState(false);
        }
    }
    
    @PostConstruct
    public void start() {
        if (client != null) {
            initializeLeaderLatch();
        } else {
            log.info("Leader election disabled - no Zookeeper connection");
        }
    }
    
    private void initializeLeaderLatch() {
        try {
            log.info("Initializing leader election for node {}", nodeId);
            leaderLatch = new LeaderLatch(client, "/leader", nodeId);
            leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    setLeaderState(true);
                }
                
                @Override
                public void notLeader() {
                    setLeaderState(false);
                }
            });
            
            // 리더 선출 시작
            leaderLatch.start();
            
            // 리더 선출 완료 대기 (타임아웃 증가)
            if (leaderLatch.await(30, TimeUnit.SECONDS)) {
                isElectionEnabled = true;
                // 초기 상태 설정
                setLeaderState(leaderLatch.hasLeadership());
                log.info("Leader election initialized. This node {} leadership", 
                    leaderLatch.hasLeadership() ? "has" : "does not have");
            } else {
                log.warn("Leader election initialization timed out");
                isElectionEnabled = false;
            }
        } catch (Exception e) {
            log.error("Failed to initialize leader election: {}", e.getMessage());
            isElectionEnabled = false;
        }
    }
    
    @PreDestroy
    public void stop() {
        if (leaderLatch != null) {
            try {
                leaderLatch.close();
                log.info("Leader election service stopped");
            } catch (Exception e) {
                log.error("Failed to stop leader election: {}", e.getMessage());
            }
        }
    }
    
    private void setLeaderState(boolean leader) {
        if (!isElectionEnabled) {
            log.debug("Leader election is not enabled - ignoring state change");
            return;
        }
        this.isLeader = leader;
        eventPublisher.publishEvent(new LeaderElectionEvent(leader, nodeId));
        log.info("👑 Node {} is now {}", nodeId, leader ? "LEADER" : "FOLLOWER");
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