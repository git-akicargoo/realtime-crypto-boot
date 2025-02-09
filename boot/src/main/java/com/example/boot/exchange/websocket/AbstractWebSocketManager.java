package com.example.boot.exchange.websocket;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.config.ExchangeConfigVO;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractWebSocketManager implements WebSocketManager {
    
    protected final ExchangeConfigVO config;
    protected final WebSocketClient client;
    
    @Getter
    protected final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void init() {
        log.info("Initializing WebSocket Manager");
        connectToExchanges();
    }

    protected void connectToExchanges() {
        connectToBinance();
        connectToUpbit();
        connectToBithumb();
    }

    private void connectToBinance() {
        String url = config.getWebsocket().getBinance();
        log.info("Connecting to Binance WebSocket: {}", url);
        client.execute(
            URI.create(url),
            session -> {
                log.info("Connected to Binance WebSocket");
                sessions.put("binance", session);
                return session.receive()
                    .doOnNext(message -> handleMessage("binance", message))
                    .doOnError(error -> log.error("Error in Binance session", error))
                    .then();
            }
        ).subscribe(
            null,
            error -> log.error("Failed to connect to Binance", error)
        );
    }

    private void connectToUpbit() {
        String url = config.getWebsocket().getUpbit();
        log.info("Connecting to Upbit WebSocket: {}", url);
        
        client.execute(URI.create(url), session -> {
            log.info("Connected to Upbit WebSocket");
            
            // 업비트 연결 후 ping 메시지 전송
            String pingMessage = "[{\"ticket\":\"PING\"}]";
            return session.send(Mono.just(session.binaryMessage(
                factory -> factory.wrap(pingMessage.getBytes(StandardCharsets.UTF_8)))))
                .then(Mono.fromRunnable(() -> {
                    sessions.put("upbit", session);  // ping 전송 후 세션 저장
                }))
                .then(session.receive()
                    .doOnNext(message -> handleMessage("upbit", message))
                    .doOnError(error -> log.error("Error in Upbit session", error))
                    .then());
        }).subscribe(
            null,
            error -> log.error("Failed to connect to Upbit", error)  // 에러 핸들링 추가
        );
    }

    private void connectToBithumb() {
        String url = config.getWebsocket().getBithumb();
        log.info("Connecting to Bithumb WebSocket: {}", url);
        client.execute(
            URI.create(url),
            session -> {
                log.info("Connected to Bithumb WebSocket");
                sessions.put("bithumb", session);
                return session.receive()
                    .doOnNext(message -> handleMessage("bithumb", message))
                    .doOnError(error -> log.error("Error in Bithumb session", error))
                    .then();
            }
        ).subscribe(
            null,
            error -> log.error("Failed to connect to Bithumb", error)
        );
    }

    private void handleMessage(String exchange, WebSocketMessage message) {
        try {
            String payload;
            if (message.getType() == WebSocketMessage.Type.TEXT) {
                payload = message.getPayloadAsText();
                log.info("Received text message from {}: {}", exchange.toUpperCase(), payload);
            } else if (message.getType() == WebSocketMessage.Type.BINARY) {
                byte[] bytes = new byte[message.getPayload().readableByteCount()];
                message.getPayload().read(bytes);
                payload = new String(bytes, StandardCharsets.UTF_8);
                log.info("Received binary message from {}: {}", exchange.toUpperCase(), payload);
            } else {
                log.warn("Unsupported message type from {}: {}", exchange, message.getType());
                return;
            }
            handleExchangeData(exchange, payload);
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", exchange, e.getMessage(), e);
        }
    }

    @Override
    public boolean isSessionActive(String exchange) {
        WebSocketSession session = sessions.get(exchange.toLowerCase());
        return session != null && session.isOpen();
    }

    @Override
    public WebSocketSession getSession(String exchange) {
        return sessions.get(exchange.toLowerCase());
    }

    @Override
    public void sendSubscribe(String exchange, String symbol) {
        String currency = switch (exchange.toLowerCase()) {
            case "binance" -> "usdt";
            case "upbit", "bithumb" -> "krw";
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        String subscribeMessage = createSubscribeMessage(exchange, symbol, currency);
        log.info("Sending subscribe message to {}: {}", exchange.toUpperCase(), subscribeMessage);
        
        if ("upbit".equals(exchange.toLowerCase())) {
            sendBinaryMessage(exchange, subscribeMessage.getBytes(StandardCharsets.UTF_8))
                .subscribe();
        } else {
            sendTextMessage(exchange, subscribeMessage)
                .subscribe();
        }
    }

    @Override
    public void sendUnsubscribe(String exchange, String symbol) {
        String currency = switch (exchange.toLowerCase()) {
            case "binance" -> "usdt";
            case "upbit", "bithumb" -> "krw";
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        String unsubscribeMessage = createUnsubscribeMessage(exchange, symbol, currency);
        log.info("Sending unsubscribe message to {}: {}", exchange.toUpperCase(), unsubscribeMessage);
        
        if ("upbit".equals(exchange.toLowerCase())) {
            sendBinaryMessage(exchange, unsubscribeMessage.getBytes(StandardCharsets.UTF_8))
                .subscribe();
        } else {
            sendTextMessage(exchange, unsubscribeMessage)
                .subscribe();
        }
    }

    @Override
    public Mono<Void> sendTextMessage(String exchange, String message) {
        WebSocketSession session = sessions.get(exchange.toLowerCase());
        if (session == null || !session.isOpen()) {
            return Mono.error(new IllegalStateException("WebSocket session is not available for " + exchange));
        }
        return session.send(Mono.just(session.textMessage(message)));
    }

    @Override
    public Mono<Void> sendBinaryMessage(String exchange, byte[] message) {
        WebSocketSession session = sessions.get(exchange.toLowerCase());
        if (session == null || !session.isOpen()) {
            return Mono.error(new IllegalStateException("WebSocket session is not available for " + exchange));
        }
        return session.send(Mono.just(session.binaryMessage(
            factory -> factory.wrap(message)
        )));
    }

    private String createSubscribeMessage(String exchange, String symbol, String currency) {
        var format = switch (exchange.toLowerCase()) {
            case "binance" -> config.getMessageFormat().getBinance().getSubscribe();
            case "upbit" -> config.getMessageFormat().getUpbit().getSubscribe();
            case "bithumb" -> config.getMessageFormat().getBithumb().getSubscribe();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        // 각 거래소별 심볼 포맷 생성
        String symbolFormat = switch (exchange.toLowerCase()) {
            case "binance" -> symbol.toLowerCase() + currency.toLowerCase();
            case "upbit" -> currency.toUpperCase() + "-" + symbol.toUpperCase();
            case "bithumb" -> symbol.toUpperCase() + "_" + currency.toUpperCase();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        return String.format(format, symbolFormat);
    }

    private String createUnsubscribeMessage(String exchange, String symbol, String currency) {
        var format = switch (exchange.toLowerCase()) {
            case "binance" -> config.getMessageFormat().getBinance().getUnsubscribe();
            case "upbit" -> config.getMessageFormat().getUpbit().getUnsubscribe();
            case "bithumb" -> config.getMessageFormat().getBithumb().getUnsubscribe();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        // 각 거래소별 심볼 포맷 생성
        String symbolFormat = switch (exchange.toLowerCase()) {
            case "binance" -> symbol.toLowerCase() + currency.toLowerCase();
            case "upbit" -> currency.toUpperCase() + "-" + symbol.toUpperCase();
            case "bithumb" -> symbol.toUpperCase() + "_" + currency.toUpperCase();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        
        return String.format(format, symbolFormat);
    }
} 