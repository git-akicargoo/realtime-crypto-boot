package com.example.boot.common.session.registry;

import java.util.Collection;

import com.example.boot.common.session.model.ClientSession;

public interface SessionRegistry {
    void registerSession(String clientId, ClientSession session);
    void removeSession(String sessionId);
    Collection<ClientSession> getActiveSessions();
    int getActiveSessionCount();
    ClientSession getSession(String sessionId);
} 