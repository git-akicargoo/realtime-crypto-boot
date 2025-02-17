package com.example.boot.exchange.layer4_distribution.common.event;

import lombok.Getter;

@Getter
public class LeaderElectionEvent {
    private final boolean isLeader;
    private final String nodeId;
    private final String role;

    public LeaderElectionEvent(boolean isLeader, String nodeId) {
        this.isLeader = isLeader;
        this.nodeId = nodeId;
        this.role = isLeader ? "LEADER" : "FOLLOWER";
    }

    @Override
    public String toString() {
        return String.format("LeaderElectionEvent{role=%s, nodeId='%s'}", role, nodeId);
    }
} 