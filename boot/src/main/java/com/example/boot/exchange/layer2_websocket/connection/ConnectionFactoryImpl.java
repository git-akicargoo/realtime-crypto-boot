package com.example.boot.exchange.layer2_websocket.connection;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandlerImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionFactoryImpl implements ConnectionFactory {
    private final WebSocketClient webSocketClient;

    @Override
    public Flux<MessageHandler> createConnection(String exchange, String url) {
        Sinks.Many<MessageHandler> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        webSocketClient.execute(
            URI.create(url),
            session -> {
                MessageHandler handler = new MessageHandlerImpl(session, exchange);
                sink.tryEmitNext(handler);
                return handler.receiveMessage().then();
            }
        ).subscribe(
            null,
            error -> {
                log.error("Failed to connect to {}: {}", exchange, error.getMessage());
                sink.tryEmitError(error);
            },
            () -> {
                log.info("Connection closed for {}", exchange);
                sink.tryEmitComplete();
            }
        );

        return sink.asFlux()
            .doOnNext(handler -> log.info("Successfully connected to {}", exchange))
            .doOnError(error -> log.error("Failed to connect to {}: {}", exchange, error.getMessage()));
    }
} 