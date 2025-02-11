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
    private final Flux<String> messageStream;
    private volatile boolean connected;

    public MessageHandlerImpl(WebSocketSession session, String exchange) {
        this.session = session;
        this.exchange = exchange;
        this.connected = true;

        this.messageStream = session.receive()
            .doOnSubscribe(s -> {
                log.info("Message stream started for {}", exchange);
                connected = true;
            })
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(message -> {
                if (log.isDebugEnabled()) {
                    log.debug("Received from {}: {}", exchange, 
                        message.substring(0, Math.min(message.length(), 100)));
                }
            })
            .doOnError(error -> {
                log.error("Error in message stream from {}: {}", exchange, error.getMessage());
                connected = false;
            })
            .doOnComplete(() -> {
                log.info("Message stream completed for {}", exchange);
                connected = false;
            })
            .publish()
            .autoConnect(1)  // refCount(1) 대신 autoConnect(1) 사용
            .onBackpressureBuffer(
                256,
                dropped -> log.warn("Dropped message from {} due to backpressure", exchange)
            );
    }

    @Override
    public Flux<String> receiveMessage() {
        if (!connected) {
            return Flux.error(new IllegalStateException("WebSocket is not connected"));
        }
        
        return messageStream
            .doOnSubscribe(s -> log.debug("New subscription to {} message stream", exchange))
            .doOnCancel(() -> log.debug("Subscription to {} message stream cancelled", exchange));
    }

    @Override
    public Flux<Void> sendMessage(String message) {
        if (!connected) {
            return Flux.error(new IllegalStateException("WebSocket is not connected"));
        }

        return Mono.just(message)
            .map(msg -> session.textMessage(msg))
            .flatMap(webSocketMessage -> session.send(Mono.just(webSocketMessage)))
            .doOnNext(v -> log.debug("Message sent to {}: {}", exchange, message))
            .doOnError(error -> log.error("Failed to send message to {}: {}", exchange, error.getMessage()))
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
        connected = false;
        return session.close()
            .doOnSubscribe(s -> log.info("Disconnecting from {}", exchange))
            .flux();
    }

    @Override
    public boolean isConnected() {
        return connected && session.isOpen();
    }
} 