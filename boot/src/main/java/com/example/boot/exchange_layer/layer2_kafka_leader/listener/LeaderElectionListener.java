package com.example.boot.exchange_layer.layer2_kafka_leader.listener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.example.boot.exchange_layer.layer1_websocket_manager.connection.WebSocketConnector;
import com.example.boot.exchange_layer.layer2_kafka_leader.event.LeaderElectedEvent;
import com.example.boot.exchange_layer.layer3_exchange_protocol.ExchangeProtocol;
import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;
import com.example.boot.exchange_layer.layer4_subscription.store.SubscriptionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderElectionListener {
    
    private final WebSocketConnector webSocketConnector;
    private final SubscriptionStore subscriptionStore;
    private final List<ExchangeProtocol> protocols;
    
    @Value("${exchange.websocket.binance}")
    private String binanceUrl;
    
    @Value("${exchange.websocket.upbit}")
    private String upbitUrl;
    
    @Value("${exchange.websocket.bithumb}")
    private String bithumbUrl;
    
    @Value("${exchange.message-format.binance.subscribe}")
    private String binanceFormat;
    
    @Value("${exchange.message-format.upbit.subscribe}")
    private String upbitFormat;
    
    @Value("${exchange.message-format.bithumb.subscribe}")
    private String bithumbFormat;
    
    @EventListener
    public void handleLeaderElected(LeaderElectedEvent event) {
        initializeConnections()
            .then(recoverSubscriptions())
            .subscribe(
                null,
                error -> log.error("Failed to initialize leader state", error),
                () -> log.info("Leader initialization completed")
            );
    }
    
    private Mono<Void> initializeConnections() {
        List<Mono<WebSocketSession>> connectionTasks = Arrays.asList(
            connectToExchange("binance", binanceUrl),
            connectToExchange("upbit", upbitUrl),
            connectToExchange("bithumb", bithumbUrl)
        );
        
        return Mono.when(connectionTasks)
            .doOnSuccess(v -> log.info("All exchange connections initialized"))
            .then();
    }
    
    private Mono<WebSocketSession> connectToExchange(String exchange, String url) {
        return webSocketConnector.connect(exchange, url)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .doBeforeRetry(signal -> 
                    log.warn("Retrying connection to {} (attempt: {})", 
                        exchange, signal.totalRetries() + 1))
            )
            .doOnSuccess(session -> log.info("Connected to {} WebSocket", exchange))
            .doOnError(e -> log.error("Failed to connect to {} WebSocket", exchange, e));
    }
    
    private Mono<Void> recoverSubscriptions() {
        return Mono.defer(() -> {
            List<Mono<Void>> tasks = Arrays.asList("binance", "upbit", "bithumb")
                .stream()
                .map(exchange -> {
                    Set<CurrencyPair> pairs = subscriptionStore.getSubscriptions(exchange);
                    if (pairs.isEmpty()) {
                        return Mono.<Void>empty();
                    }
                    return resubscribe(exchange, pairs);
                })
                .collect(Collectors.toList());
            
            return Mono.when(tasks)
                .doOnSuccess(v -> log.info("All subscriptions recovered"))
                .then();
        });
    }
    
    private Mono<Void> resubscribe(String exchange, Set<CurrencyPair> pairs) {
        ExchangeProtocol protocol = findProtocol(exchange);
        if (protocol == null) {
            log.warn("No protocol found for exchange: {}", exchange);
            return Mono.empty();
        }
        
        String message = protocol.createSubscribeMessage(
            new ArrayList<>(pairs),
            getMessageFormat(exchange)
        );
        
        return webSocketConnector.sendMessage(exchange, message)
            .doOnSuccess(v -> log.info("Resubscribed to {} pairs on {}", pairs.size(), exchange))
            .doOnError(e -> log.error("Failed to resubscribe on {}: {}", exchange, e.getMessage()));
    }
    
    private ExchangeProtocol findProtocol(String exchange) {
        return protocols.stream()
            .filter(p -> p.supports(exchange))
            .findFirst()
            .orElse(null);
    }
    
    private String getMessageFormat(String exchange) {
        return switch (exchange.toLowerCase()) {
            case "binance" -> binanceFormat;
            case "upbit" -> upbitFormat;
            case "bithumb" -> bithumbFormat;
            default -> {
                log.warn("Unknown exchange: {}, using default format", exchange);
                yield "%s";
            }
        };
    }
} 