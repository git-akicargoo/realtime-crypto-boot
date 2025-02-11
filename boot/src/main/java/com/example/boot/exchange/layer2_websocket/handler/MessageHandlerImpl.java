package com.example.boot.exchange.layer2_websocket.handler;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class MessageHandlerImpl implements MessageHandler {
    private final WebSocketSession session;
    private final String exchange;
    private volatile boolean connected;

    public MessageHandlerImpl(WebSocketSession session, String exchange) {
        this.session = session;
        this.exchange = exchange;
        this.connected = true;
    }

    @Override
    public Flux<String> receiveMessage() {
        return session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(message -> log.debug("Received from {}: {}", exchange, message))
            .doOnError(error -> {
                log.error("Error receiving from {}: {}", exchange, error.getMessage());
                connected = false;
            })
            .doOnComplete(() -> {
                log.debug("Receive completed for {}", exchange);
                connected = false;
            });
    }

    @Override
    public Flux<Void> sendMessage(String message) {
        return Mono.just(message)
            .map(msg -> session.textMessage(msg))
            .flatMap(webSocketMessage -> session.send(Mono.just(webSocketMessage)))
            .doOnSubscribe(s -> log.debug("Sending to {}: {}", exchange, message))
            .doOnError(error -> {
                log.error("Error sending to {}: {}", exchange, error.getMessage());
                connected = false;
            })
            .flux();
    }

    @Override
    public Flux<Void> sendBinaryMessage(byte[] message) {
        return Mono.just(message)
            .map(msg -> session.binaryMessage(factory -> factory.wrap(msg)))
            .flatMap(webSocketMessage -> session.send(Mono.just(webSocketMessage)))
            .doOnSubscribe(s -> log.debug("Sending binary message to {}", exchange))
            .doOnError(error -> {
                log.error("Error sending binary message to {}: {}", exchange, error.getMessage());
                connected = false;
            })
            .flux();
    }

    @Override
    public Flux<Void> disconnect() {
        return Mono.fromRunnable(() -> connected = false)
            .then(session.close())
            .doOnSubscribe(s -> log.debug("Disconnecting from {}", exchange))
            .flux();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
} 