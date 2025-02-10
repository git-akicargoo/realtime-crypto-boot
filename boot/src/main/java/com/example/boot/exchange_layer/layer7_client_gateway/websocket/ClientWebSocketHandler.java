package com.example.boot.exchange_layer.layer7_client_gateway.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler extends TextWebSocketHandler {
    
    private final ClientSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        log.info("Client connected: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.get("type").asText();
            String symbol = node.get("symbol").asText();
            String quoteCurrency = node.get("quoteCurrency").asText();
            
            CurrencyPair pair = new CurrencyPair(quoteCurrency, symbol);
            
            switch (type) {
                case "subscribe":
                    sessionManager.addSubscription(session.getId(), pair);
                    log.debug("Client {} subscribed to {}", session.getId(), pair);
                    break;
                case "unsubscribe":
                    sessionManager.removeSubscription(session.getId(), pair);
                    log.debug("Client {} unsubscribed from {}", session.getId(), pair);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message: sessionId={}, error={}", 
                     session.getId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
        log.info("Client disconnected: {}", session.getId());
    }
} 