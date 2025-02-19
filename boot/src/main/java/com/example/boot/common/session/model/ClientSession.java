package com.example.boot.common.session.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClientSession {
    private String sessionId;
    private String clientId;
    private SessionType sessionType;
    private Object session;  // WebSocketSession, RedisSession 등
    
    public enum SessionType {
        WEBSOCKET,
        REDIS,
        DATABASE
    }
} 