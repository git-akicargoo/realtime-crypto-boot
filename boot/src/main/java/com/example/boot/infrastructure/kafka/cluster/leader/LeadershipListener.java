package com.example.boot.infrastructure.kafka.cluster.leader;

public interface LeadershipListener {
    void onLeadershipGranted();
    void onLeadershipRevoked();
} 