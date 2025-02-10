package com.example.boot.exchange_layer.layer1_websocket_manager.session;

import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

public interface SessionManager {
    Mono<WebSocketSession> registerSession(String exchange, WebSocketSession session);
    Mono<WebSocketSession> getSession(String exchange);
    Mono<Boolean> isSessionActive(String exchange);
    Mono<Void> removeSession(String exchange);
    Mono<Void> removeAllSessions();
} 