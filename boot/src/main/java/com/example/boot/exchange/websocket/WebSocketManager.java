package com.example.boot.exchange.websocket;

import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

public interface WebSocketManager {
    void init();
    boolean isLeader();
    void tryBecomeLeader();
    boolean isSessionActive(String exchange);
    WebSocketSession getSession(String exchange);
    
    // 메시지 전송 관련
    void sendSubscribe(String exchange, String symbol);
    void sendUnsubscribe(String exchange, String symbol);
    Mono<Void> sendTextMessage(String exchange, String message);
    Mono<Void> sendBinaryMessage(String exchange, byte[] message);
    
    // 메시지 수신 관련
    void handleExchangeData(String exchange, String data);
} 