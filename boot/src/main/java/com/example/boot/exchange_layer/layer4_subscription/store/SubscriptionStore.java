package com.example.boot.exchange_layer.layer4_subscription.store;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SubscriptionStore {
    private final Map<String, Set<CurrencyPair>> subscriptions = new ConcurrentHashMap<>();
    
    public void addSubscription(String exchange, CurrencyPair pair) {
        subscriptions.computeIfAbsent(exchange, k -> ConcurrentHashMap.newKeySet())
                    .add(pair);
        log.debug("Added subscription: {} - {}", exchange, pair);
    }
    
    public void removeSubscription(String exchange, CurrencyPair pair) {
        Set<CurrencyPair> pairs = subscriptions.get(exchange);
        if (pairs != null) {
            pairs.remove(pair);
            log.debug("Removed subscription: {} - {}", exchange, pair);
        }
    }
    
    public boolean isSubscribed(String exchange, CurrencyPair pair) {
        Set<CurrencyPair> pairs = subscriptions.get(exchange);
        return pairs != null && pairs.contains(pair);
    }
    
    public Set<CurrencyPair> getSubscriptions(String exchange) {
        return subscriptions.getOrDefault(exchange, Collections.emptySet());
    }
    
    public void clearSubscriptions(String exchange) {
        subscriptions.remove(exchange);
        log.debug("Cleared all subscriptions for exchange: {}", exchange);
    }
} 