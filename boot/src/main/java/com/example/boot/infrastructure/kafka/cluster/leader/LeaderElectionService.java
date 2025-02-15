package com.example.boot.infrastructure.kafka.cluster.leader;

public interface LeaderElectionService {
    void start();
    void stop();
    boolean isLeader();
    String getLeaderId();
    void addLeadershipListener(LeadershipListener listener);
} 