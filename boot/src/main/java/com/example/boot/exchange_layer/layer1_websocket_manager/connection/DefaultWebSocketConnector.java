package com.example.boot.exchange_layer.layer1_websocket_manager.connection;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.exchange_layer.layer1_websocket_manager.session.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWebSocketConnector implements WebSocketConnector {
    
    private final WebSocketClient client;
    private final SessionManager sessionManager;
    
    @Override
    public Mono<WebSocketSession> connect(String exchange, String url) {
        log.info("Connecting to {} WebSocket: {}", exchange, url);
        
        return client.execute(URI.create(url), session -> {
            log.info("Connected to {} WebSocket", exchange);
            return sessionManager.registerSession(exchange, session)
                   .then();
        })
        .then(sessionManager.getSession(exchange));
    }
    
    @Override
    public Mono<Void> disconnect(String exchange) {
        return sessionManager.removeSession(exchange);
    }
    
    @Override
    public Mono<Void> disconnectAll() {
        return sessionManager.removeAllSessions();
    }
    
    @Override
    public Mono<Void> sendMessage(String exchange, String message) {
        return sessionManager.getSession(exchange)
            .flatMap(session -> {
                WebSocketMessage webSocketMessage = session.textMessage(message);
                log.debug("Sending message to {}: {}", exchange, message);
                return session.send(Mono.just(webSocketMessage));
            })
            .doOnError(error -> log.error("Error sending message to {}: {}", exchange, error.getMessage()));
    }
    
    @Override
    public Mono<Void> sendBinaryMessage(String exchange, byte[] message) {
        return sessionManager.getSession(exchange)
            .flatMap(session -> {
                WebSocketMessage webSocketMessage = session.binaryMessage(
                    factory -> factory.wrap(message)
                );
                log.debug("Sending binary message to {}: {}", exchange, new String(message));
                return session.send(Mono.just(webSocketMessage));
            })
            .doOnError(error -> log.error("Error sending binary message to {}: {}", exchange, error.getMessage()));
    }
} 