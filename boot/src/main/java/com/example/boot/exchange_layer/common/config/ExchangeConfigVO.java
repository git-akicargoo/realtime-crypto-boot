package com.example.boot.exchange_layer.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "exchange")
@Getter
@Setter
public class ExchangeConfigVO {
    private WebSocket websocket;
    private MessageFormat messageFormat;
    private Common common;
    private Exchanges exchanges;

    @Getter
    @Setter
    public static class WebSocket {
        private String binance;
        private String upbit; 
        private String bithumb;
    }

    @Getter
    @Setter
    public static class MessageFormat {
        private ExchangeFormat binance;
        private ExchangeFormat upbit;
        private ExchangeFormat bithumb;
    }

    @Getter
    @Setter
    public static class ExchangeFormat {
        private String subscribe;
        private String unsubscribe;
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
} 