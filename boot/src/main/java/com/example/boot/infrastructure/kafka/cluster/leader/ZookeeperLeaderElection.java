package com.example.boot.infrastructure.kafka.cluster.leader;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.state.ConnectionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class ZookeeperLeaderElection extends LeaderSelectorListenerAdapter implements LeaderElectionService {
    
    private final CuratorFramework curatorFramework;
    private LeaderSelector leaderSelector;
    private final List<LeadershipListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicBoolean zookeeperAvailable = new AtomicBoolean(false);
    
    @Value("${spring.application.name:exchange-service}")
    private String applicationName;
    
    @Value("${server.port}")
    private String serverPort;
    
    @Value("${zookeeper.connect-string:localhost:2181}")
    private String connectString;
    
    private String leaderId;
    
    @PostConstruct
    public void init() {
        log.info("Initializing Zookeeper Leader Election with connect string: {}", connectString);
        checkZookeeperAvailability();
    }
    
    @Scheduled(fixedRate = 10000)
    public void checkZookeeperAvailability() {
        try {
            curatorFramework.checkExists().forPath("/");
            if (!zookeeperAvailable.get()) {
                zookeeperAvailable.set(true);
                initializeLeaderSelector();
            }
            log.info("Zookeeper Health Check - Config: [{}], Server: Running, Connection: Connected", 
                     connectString);
        } catch (Exception e) {
            if (zookeeperAvailable.get()) {
                zookeeperAvailable.set(false);
                stopLeaderSelector();
            }
            log.info("Zookeeper Health Check - Config: [{}], Server: Not Running, Connection: Disconnected", 
                     connectString);
        }
    }
    
    private void initializeLeaderSelector() {
        if (leaderSelector == null) {
            String leaderPath = "/leader/" + applicationName;
            String participantId = applicationName + "-" + serverPort;
            
            leaderSelector = new LeaderSelector(curatorFramework, leaderPath, this);
            leaderSelector.setId(participantId);
            leaderSelector.autoRequeue();
            start();
        }
    }
    
    private void stopLeaderSelector() {
        if (leaderSelector != null) {
            stop();
            leaderSelector = null;
        }
    }
    
    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        leaderId = leaderSelector.getId();
        isLeader.set(true);
        log.info("🎉 Leadership acquired - This instance [{}] is now the LEADER", leaderId);
        listeners.forEach(LeadershipListener::onLeadershipGranted);
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.warn("Leadership interrupted for instance [{}]", leaderId);
            Thread.currentThread().interrupt();
        } finally {
            isLeader.set(false);
            log.info("👋 Leadership released - This instance [{}] is no longer the leader", leaderId);
            listeners.forEach(LeadershipListener::onLeadershipRevoked);
        }
    }
    
    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        if (newState == ConnectionState.SUSPENDED || newState == ConnectionState.LOST) {
            isLeader.set(false);
            log.warn("Leadership lost due to: {}", newState);
            listeners.forEach(LeadershipListener::onLeadershipRevoked);
        }
    }
    
    @Override
    public void start() {
        log.info("Starting leader election process...");
        leaderSelector.start();
    }
    
    @Override
    public void stop() {
        log.info("Stopping leader election process...");
        leaderSelector.close();
    }
    
    @Override
    public boolean isLeader() {
        return isLeader.get();
    }
    
    @Override
    public String getLeaderId() {
        return leaderId;
    }
    
    @Override
    public void addLeadershipListener(LeadershipListener listener) {
        listeners.add(listener);
    }
    
    @PreDestroy
    public void destroy() {
        stop();
    }
    
    public boolean isZookeeperAvailable() {
        return zookeeperAvailable.get();
    }
    
    @Scheduled(fixedRate = 30000)
    public void logLeadershipStatus() {
        if (zookeeperAvailable.get()) {
            if (isLeader()) {
                log.info("🚀 Leadership Status: This instance [{}] is currently the LEADER", leaderId);
            } else {
                log.info("👥 Leadership Status: This instance [{}] is currently a FOLLOWER", 
                    applicationName + "-" + serverPort);
            }
        }
    }
} 