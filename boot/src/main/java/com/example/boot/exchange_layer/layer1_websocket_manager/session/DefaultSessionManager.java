package com.example.boot.exchange_layer.layer1_websocket_manager.session;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultSessionManager implements SessionManager {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public Mono<WebSocketSession> registerSession(String exchange, WebSocketSession session) {
        return Mono.fromCallable(() -> {
            sessions.put(exchange.toLowerCase(), session);
            log.info("Session registered for exchange: {}", exchange);
            
            session.closeStatus()
                .subscribe(status -> {
                    log.info("Session closed for exchange: {} with status: {}", exchange, status);
                    removeSession(exchange).subscribe();
                });
                
            return session;
        });
    }
    
    @Override
    public Mono<WebSocketSession> getSession(String exchange) {
        return Mono.justOrEmpty(sessions.get(exchange.toLowerCase()));
    }
    
    @Override
    public Mono<Boolean> isSessionActive(String exchange) {
        return getSession(exchange)
            .map(WebSocketSession::isOpen)
            .defaultIfEmpty(false);
    }
    
    @Override
    public Mono<Void> removeSession(String exchange) {
        return Mono.fromRunnable(() -> {
            WebSocketSession session = sessions.remove(exchange.toLowerCase());
            if (session != null && session.isOpen()) {
                session.close()
                    .subscribe(null,
                        error -> log.error("Error closing session for {}", exchange, error),
                        () -> log.info("Session removed for exchange: {}", exchange)
                    );
            }
        });
    }
    
    @Override
    public Mono<Void> removeAllSessions() {
        return Mono.fromRunnable(() -> 
            sessions.forEach((exchange, session) -> 
                removeSession(exchange).subscribe()
            )
        );
    }
}