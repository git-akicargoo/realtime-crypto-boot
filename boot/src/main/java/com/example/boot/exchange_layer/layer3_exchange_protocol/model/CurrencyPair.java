package com.example.boot.exchange_layer.layer3_exchange_protocol.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CurrencyPair {
    private final String quoteCurrency; // USDT, KRW 등
    private final String symbol;        // BTC, ETH 등
    
    public String formatForBinance() {
        return symbol.toLowerCase() + quoteCurrency.toLowerCase();  // btcusdt
    }
    
    public String formatForUpbit() {
        return quoteCurrency.toUpperCase() + "-" + symbol.toUpperCase(); // KRW-BTC
    }
    
    public String formatForBithumb() {
        return symbol.toUpperCase() + "_" + quoteCurrency.toUpperCase(); // BTC_KRW
    }
    
    @Override
    public String toString() {
        return quoteCurrency.toUpperCase() + "-" + symbol.toUpperCase();
    }
} 