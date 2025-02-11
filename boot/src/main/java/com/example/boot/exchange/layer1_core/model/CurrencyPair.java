package com.example.boot.exchange.layer1_core.model;

public record CurrencyPair(
    String quoteCurrency,  // USDT, KRW 등
    String symbol         // BTC, ETH 등
) {
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