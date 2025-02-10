package com.example.boot.exchange_layer.layer7_client_gateway.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;
import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionManager {
    
    private final ObjectMapper objectMapper;
    
    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // sessionId -> Set<CurrencyPair>
    private final Map<String, Set<CurrencyPair>> subscriptions = new ConcurrentHashMap<>();
    
    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        subscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
    }
    
    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
    }
    
    public void addSubscription(String sessionId, CurrencyPair pair) {
        subscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                    .add(pair);
    }
    
    public void removeSubscription(String sessionId, CurrencyPair pair) {
        Set<CurrencyPair> pairs = subscriptions.get(sessionId);
        if (pairs != null) {
            pairs.remove(pair);
        }
    }
    
    public void broadcastMessage(NormalizedMessage message) {
        CurrencyPair pair = new CurrencyPair(message.getQuoteCurrency(), message.getSymbol());
        
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            TextMessage webSocketMessage = new TextMessage(messageJson);
            
            sessions.forEach((sessionId, session) -> {
                Set<CurrencyPair> subscribedPairs = subscriptions.get(sessionId);
                if (subscribedPairs != null && 
                    subscribedPairs.contains(pair) && 
                    session.isOpen()) {
                    try {
                        session.sendMessage(webSocketMessage);
                        log.debug("Price info sent: sessionId={}, exchange={}, pair={}", 
                                sessionId, message.getExchange(), pair);
                    } catch (Exception e) {
                        log.error("Failed to send to client: sessionId={}", sessionId, e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize message: {}", message, e);
        }
    }
} 