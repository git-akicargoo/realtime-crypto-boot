package com.example.boot.common.session.service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.boot.common.session.model.ClientSession;
import com.example.boot.common.session.registry.SessionRegistry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DefaultSessionRegistry implements SessionRegistry {
    private final ConcurrentHashMap<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> clientIdToSessionId = new ConcurrentHashMap<>();
    
    @Override
    public void registerSession(String clientId, ClientSession session) {
        String oldSessionId = clientIdToSessionId.get(clientId);
        if (oldSessionId != null) {
            removeSession(oldSessionId);
        }
        
        activeSessions.put(session.getSessionId(), session);
        clientIdToSessionId.put(clientId, session.getSessionId());
        log.info("Session registered - clientId: {}, sessionId: {}, type: {}", 
            clientId, session.getSessionId(), session.getSessionType());
    }
    
    @Override
    public void removeSession(String sessionId) {
        ClientSession session = activeSessions.remove(sessionId);
        if (session != null) {
            clientIdToSessionId.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
            log.info("Session removed - sessionId: {}, type: {}", 
                sessionId, session.getSessionType());
        }
    }
    
    @Override
    public Collection<ClientSession> getActiveSessions() {
        return activeSessions.values();
    }
    
    @Override
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    @Override
    public ClientSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
} 