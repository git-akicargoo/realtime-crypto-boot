package com.example.boot.exchange.layer3_data_converter.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer1_core.config.ExchangeConfig;
import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.model.ExchangeMessage;
import com.example.boot.exchange.layer1_core.protocol.BaseExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.connection.ConnectionFactory;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;
import com.example.boot.exchange.layer3_data_converter.converter.ExchangeDataConverter;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ExchangeDataIntegrationServiceImpl implements ExchangeDataIntegrationService {
    private final Map<String, ExchangeDataConverter> converters;
    private final Map<String, BaseExchangeProtocol> protocols;
    private final ConnectionFactory connectionFactory;
    private final ExchangeConfig config;
    
    // 활성화된 WebSocket 핸들러 관리
    private final Map<String, MessageHandler> activeHandlers = new ConcurrentHashMap<>();

    public ExchangeDataIntegrationServiceImpl(
        List<ExchangeDataConverter> converterList,
        List<BaseExchangeProtocol> protocolList,
        ConnectionFactory connectionFactory,
        ExchangeConfig config
    ) {
        this.converters = converterList.stream()
            .collect(Collectors.toMap(
                ExchangeDataConverter::getExchangeName,
                converter -> converter
            ));
        this.protocols = protocolList.stream()
            .collect(Collectors.toMap(
                BaseExchangeProtocol::getExchangeName,
                protocol -> protocol
            ));
        this.connectionFactory = connectionFactory;
        this.config = config;
    }

    @Override
    public Flux<StandardExchangeData> subscribe() {
        Map<String, List<CurrencyPair>> exchangePairs = createExchangePairsFromConfig();
        
        // 구독할 페어가 없는 거래소는 제외
        exchangePairs = exchangePairs.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
        
        if (exchangePairs.isEmpty()) {
            return Flux.error(new IllegalStateException("No valid exchange pairs configured"));
        }
        
        log.info("Subscribing to exchanges: {}", exchangePairs);
        return subscribe(exchangePairs)
            .doOnError(error -> log.error("Error in subscription: ", error))
            .retry(config.getConnection().getMaxRetryAttempts())
            .onErrorResume(e -> {
                log.error("Failed to subscribe after retries: ", e);
                return Flux.empty();
            });
    }

    private Map<String, List<CurrencyPair>> createExchangePairsFromConfig() {
        Map<String, List<CurrencyPair>> pairs = new HashMap<>();
        
        if (config.getExchanges().getBinance() != null) {
            var binancePairs = createPairsFromConfig(
                config.getCommon().getSupportedSymbols(), 
                config.getExchanges().getBinance().getSupportedCurrencies()
            );
            pairs.put("binance", binancePairs);
            log.debug("Created pairs for Binance: {}", binancePairs);
        }
        
        if (config.getExchanges().getUpbit() != null) {
            var upbitPairs = createPairsFromConfig(
                config.getCommon().getSupportedSymbols(), 
                config.getExchanges().getUpbit().getSupportedCurrencies()
            );
            pairs.put("upbit", upbitPairs);
            log.debug("Created pairs for Upbit: {}", upbitPairs);
        }
        
        if (config.getExchanges().getBithumb() != null) {
            var bithumbPairs = createPairsFromConfig(
                config.getCommon().getSupportedSymbols(), 
                config.getExchanges().getBithumb().getSupportedCurrencies()
            );
            pairs.put("bithumb", bithumbPairs);
            log.debug("Created pairs for Bithumb: {}", bithumbPairs);
        }
        
        log.info("Created exchange pairs from config: {}", pairs);
        return pairs;
    }

    private List<CurrencyPair> createPairsFromConfig(List<String> symbols, List<String> currencies) {
        return symbols.stream()
            .flatMap(symbol -> currencies.stream()
                .map(currency -> new CurrencyPair(currency, symbol)))
            .collect(Collectors.toList());
    }

    @Override
    public Flux<StandardExchangeData> subscribe(Map<String, List<CurrencyPair>> exchangePairs) {
        return Flux.fromIterable(exchangePairs.entrySet())
            .flatMap(entry -> subscribeToExchange(entry.getKey(), entry.getValue()));
    }

    private Flux<StandardExchangeData> subscribeToExchange(String exchange, List<CurrencyPair> pairs) {
        if (pairs.isEmpty()) {
            log.warn("No pairs configured for exchange: {}", exchange);
            return Flux.empty();
        }

        BaseExchangeProtocol protocol = protocols.get(exchange);
        ExchangeDataConverter converter = converters.get(exchange);
        
        if (protocol == null || converter == null) {
            log.error("Missing protocol or converter for exchange: {}", exchange);
            return Flux.empty();
        }

        String wsUrl = getWebSocketUrl(exchange);
        if (wsUrl == null) {
            log.error("Missing WebSocket URL for exchange: {}", exchange);
            return Flux.empty();
        }

        return connectionFactory.createConnection(exchange, wsUrl)
            .flatMap(handler -> {
                activeHandlers.put(exchange, handler);
                
                return sendSubscribeMessage(exchange, handler, protocol, pairs)
                    .thenMany(handler.receiveMessage())
                    .map(raw -> new ExchangeMessage(
                        exchange, 
                        raw, 
                        Instant.now(), 
                        ExchangeMessage.MessageType.TICKER
                    ))
                    .flatMap(msg -> converter.convert(msg)
                        .doOnError(e -> log.error("Error converting message from {}: {}", 
                            exchange, e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                    )
                    .doOnNext(data -> log.debug("Converted data from {}: {}", exchange, data))
                    .doOnError(error -> log.error("Error processing message from {}: {}", 
                        exchange, error.getMessage()));
            });
    }

    private Flux<Void> sendSubscribeMessage(
        String exchange, 
        MessageHandler handler, 
        BaseExchangeProtocol protocol,
        List<CurrencyPair> pairs
    ) {
        String message = protocol.createSubscribeMessage(pairs);
        return exchange.equalsIgnoreCase("upbit")
            ? handler.sendBinaryMessage(message.getBytes())
            : handler.sendMessage(message);
    }

    private String getWebSocketUrl(String exchange) {
        return switch (exchange.toLowerCase()) {
            case "binance" -> config.getWebsocket().getBinance();
            case "upbit" -> config.getWebsocket().getUpbit();
            case "bithumb" -> config.getWebsocket().getBithumb();
            default -> null;
        };
    }

    @Override
    public Mono<Void> unsubscribeAll() {
        return Flux.fromIterable(activeHandlers.entrySet())
            .flatMap(entry -> unsubscribe(entry.getKey()))
            .then();
    }

    @Override
    public Mono<Void> unsubscribe(String exchange) {
        MessageHandler handler = activeHandlers.remove(exchange);
        if (handler == null) {
            return Mono.empty();
        }

        BaseExchangeProtocol protocol = protocols.get(exchange);
        String unsubscribeMessage = protocol.createUnsubscribeMessage(List.of());
        
        return handler.sendMessage(unsubscribeMessage)
            .thenMany(handler.disconnect())
            .then();
    }
} 