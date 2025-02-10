package com.example.boot.exchange_layer.layer4_subscription;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer1_websocket_manager.connection.WebSocketConnector;
import com.example.boot.exchange_layer.layer3_exchange_protocol.ExchangeProtocol;
import com.example.boot.exchange_layer.layer4_subscription.model.SubscriptionRequest;
import com.example.boot.exchange_layer.layer4_subscription.store.SubscriptionStore;
import com.example.boot.exchange_layer.layer4_subscription.validator.SubscriptionValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSubscriptionManager implements SubscriptionManager {
    
    private final List<ExchangeProtocol> protocols;
    private final WebSocketConnector webSocketConnector;
    private final SubscriptionStore subscriptionStore;
    private final SubscriptionValidator validator;
    
    @Override
    public Mono<Void> subscribe(SubscriptionRequest request) {
        return Mono.defer(() -> {
            // 1. Validate request
            if (!isValidRequest(request)) {
                return Mono.error(new IllegalArgumentException("Invalid subscription request"));
            }
            
            // 2. Find appropriate protocol
            ExchangeProtocol protocol = findProtocol(request.getExchange());
            if (protocol == null) {
                return Mono.error(new IllegalArgumentException("Unsupported exchange: " + request.getExchange()));
            }
            
            // 3. Create subscription message
            String message = protocol.createSubscribeMessage(request.getPairs(), request.getMessageFormat());
            log.debug("Created subscribe message for {}: {}", request.getExchange(), message);
            
            // 4. Send message and update store
            return webSocketConnector.sendMessage(request.getExchange(), message)
                .doOnSuccess(v -> {
                    request.getPairs().forEach(pair -> 
                        subscriptionStore.addSubscription(request.getExchange(), pair));
                    log.info("Successfully subscribed to {} pairs on {}", 
                            request.getPairs().size(), request.getExchange());
                })
                .doOnError(error -> 
                    log.error("Failed to subscribe to {} on {}: {}", 
                            request.getPairs(), request.getExchange(), error.getMessage())
                );
        });
    }
    
    @Override
    public Mono<Void> unsubscribe(SubscriptionRequest request) {
        return Mono.defer(() -> {
            ExchangeProtocol protocol = findProtocol(request.getExchange());
            if (protocol == null) {
                return Mono.error(new IllegalArgumentException("Unsupported exchange: " + request.getExchange()));
            }
            
            String message = protocol.createUnsubscribeMessage(request.getPairs(), request.getMessageFormat());
            log.debug("Created unsubscribe message for {}: {}", request.getExchange(), message);
            
            return webSocketConnector.sendMessage(request.getExchange(), message)
                .doOnSuccess(v -> {
                    request.getPairs().forEach(pair -> 
                        subscriptionStore.removeSubscription(request.getExchange(), pair));
                    log.info("Successfully unsubscribed from {} pairs on {}", 
                            request.getPairs().size(), request.getExchange());
                })
                .doOnError(error -> 
                    log.error("Failed to unsubscribe from {} on {}: {}", 
                            request.getPairs(), request.getExchange(), error.getMessage())
                );
        });
    }
    
    private boolean isValidRequest(SubscriptionRequest request) {
        return request.getPairs().stream().allMatch(pair -> 
            validator.isValidSubscription(
                request.getExchange(),
                pair,
                request.getSupportedSymbols(),
                request.getSupportedQuoteCurrencies()
            ));
    }
    
    private ExchangeProtocol findProtocol(String exchange) {
        return protocols.stream()
            .filter(p -> p.supports(exchange))
            .findFirst()
            .orElse(null);
    }
} 