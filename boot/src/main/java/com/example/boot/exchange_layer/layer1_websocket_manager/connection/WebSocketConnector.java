package com.example.boot.exchange_layer.layer1_websocket_manager.connection;

import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

public interface WebSocketConnector {
    Mono<WebSocketSession> connect(String exchange, String url);
    Mono<Void> disconnect(String exchange);
    Mono<Void> disconnectAll();
    Mono<Void> sendMessage(String exchange, String message);
    Mono<Void> sendBinaryMessage(String exchange, byte[] message);
} 