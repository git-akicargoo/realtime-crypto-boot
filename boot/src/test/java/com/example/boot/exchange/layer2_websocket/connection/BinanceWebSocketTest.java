package com.example.boot.exchange.layer2_websocket.connection;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
public class BinanceWebSocketTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private BinanceExchangeProtocol binanceProtocol;

    @Test
    void binanceWebSocketTest() {
        // Given
        String exchange = "binance";
        String url = "wss://stream.binance.com:9443/ws";
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC"),
            new CurrencyPair("USDT", "ETH")
        );
        String messageFormat = "{\"method\": \"SUBSCRIBE\",\"params\": [\"%s@trade\"],\"id\": 1}";

        // When
        Flux<MessageHandler> connection = connectionFactory.createConnection(exchange, url);

        connection.flatMap(handler -> {
            log.info("Connected to Binance WebSocket");
            
            // Subscribe message 전송
            String subscribeMessage = binanceProtocol.createSubscribeMessage(pairs, messageFormat);
            log.info("Sending subscribe message: {}", subscribeMessage);
            
            return handler.sendMessage(subscribeMessage)
                .thenMany(handler.receiveMessage()
                    .doOnNext(msg -> log.info("Received message: {}", msg))
                    .doOnError(error -> log.error("Error: {}", error.getMessage()))
                    .take(5) // 5개의 메시지만 받고 종료
                );
        })
        .doOnComplete(() -> log.info("Test completed"))
        .subscribe();

        // 테스트가 너무 빨리 끝나지 않도록 잠시 대기
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 