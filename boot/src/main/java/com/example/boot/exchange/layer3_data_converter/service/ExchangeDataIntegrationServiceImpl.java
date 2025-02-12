package com.example.boot.exchange.layer3_data_converter.service;

import java.time.Instant;
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
    public Flux<StandardExchangeData> subscribe(Map<String, List<CurrencyPair>> exchangePairs) {
        return Flux.fromIterable(exchangePairs.entrySet())
            .flatMap(entry -> subscribeToExchange(entry.getKey(), entry.getValue()));
    }

    private Flux<StandardExchangeData> subscribeToExchange(String exchange, List<CurrencyPair> pairs) {
        BaseExchangeProtocol protocol = protocols.get(exchange);
        ExchangeDataConverter converter = converters.get(exchange);
        
        if (protocol == null || converter == null) {
            return Flux.error(new IllegalArgumentException("Unsupported exchange: " + exchange));
        }

        // config에서 직접 URL 가져오기
        String wsUrl = switch (exchange.toLowerCase()) {
            case "binance" -> config.getWebsocket().getBinance();
            case "upbit" -> config.getWebsocket().getUpbit();
            case "bithumb" -> config.getWebsocket().getBithumb();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };

        return connectionFactory.createConnection(exchange, wsUrl)
            .flatMap(handler -> {
                activeHandlers.put(exchange, handler);
                
                // 업비트만 바이너리 메시지 사용
                Flux<Void> subscription = exchange.equalsIgnoreCase("upbit")
                    ? handler.sendBinaryMessage(protocol.createSubscribeMessage(pairs).getBytes())
                    : handler.sendMessage(protocol.createSubscribeMessage(pairs));

                return subscription.thenMany(handler.receiveMessage())
                    .map(raw -> new ExchangeMessage(
                        exchange,
                        raw,
                        Instant.now(),
                        ExchangeMessage.MessageType.TRADE
                    ))
                    .flatMap(converter::convert)
                    .doOnNext(data -> log.debug("Converted data from {}: {}", exchange, data))
                    .doOnError(error -> log.error("Error processing message from {}: {}", 
                        exchange, error.getMessage()));
            });
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