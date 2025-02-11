package com.example.boot.exchange.layer1_core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "exchange", ignoreInvalidFields = true)
@Getter
@Setter
public class ExchangeConfig {
    private WebSocket websocket;
    private Common common;
    private Exchanges exchanges;
    private Connection connection;

    @Getter
    @Setter
    public static class WebSocket {
        private String binance;
        private String upbit; 
        private String bithumb;
    }

    @Getter
    @Setter
    public static class Common {
        private List<String> supportedSymbols;
    }

    @Getter
    @Setter
    public static class Exchanges {
        private Exchange binance;
        private Exchange upbit;
        private Exchange bithumb;
    }

    @Getter
    @Setter
    public static class Exchange {
        private List<String> supportedCurrencies;
    }

    @Getter
    @Setter
    public static class Connection {
        private int maxRetryAttempts = 3;
        private long reconnectDelay = 1000L;
        private long connectionTimeout = 30000L;
    }
} 