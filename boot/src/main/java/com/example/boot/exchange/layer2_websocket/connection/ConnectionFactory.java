package com.example.boot.exchange.layer2_websocket.connection;

import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;

import reactor.core.publisher.Flux;

public interface ConnectionFactory {
    Flux<MessageHandler> createConnection(String exchange, String url);
} 