package com.example.boot.exchange.layer2_websocket.handler;

import reactor.core.publisher.Flux;

public interface MessageHandler {
    Flux<String> receiveMessage();
    Flux<Void> sendMessage(String message);
    Flux<Void> sendBinaryMessage(byte[] message);
    Flux<Void> disconnect();
    boolean isConnected();
} 