package com.example.boot.exchange_layer.layer4_subscription.validator;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SubscriptionValidator {
    
    public boolean isValidSubscription(
            String exchange,
            CurrencyPair pair,
            Set<String> supportedSymbols,
            Set<String> supportedQuoteCurrencies) {
        
        boolean isValid = isValidSymbol(pair.getSymbol(), supportedSymbols) &&
                         isValidQuoteCurrency(pair.getQuoteCurrency(), supportedQuoteCurrencies);
                         
        if (!isValid) {
            log.warn("Invalid subscription request: exchange={}, pair={}", exchange, pair);
        }
        
        return isValid;
    }
    
    private boolean isValidSymbol(String symbol, Set<String> supportedSymbols) {
        return symbol != null && 
               supportedSymbols != null && 
               supportedSymbols.contains(symbol.toUpperCase());
    }
    
    private boolean isValidQuoteCurrency(String quoteCurrency, Set<String> supportedQuoteCurrencies) {
        return quoteCurrency != null && 
               supportedQuoteCurrencies != null && 
               supportedQuoteCurrencies.contains(quoteCurrency.toUpperCase());
    }
} 