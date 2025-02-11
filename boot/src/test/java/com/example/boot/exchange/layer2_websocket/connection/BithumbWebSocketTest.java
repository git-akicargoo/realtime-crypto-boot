package com.example.boot.exchange.layer2_websocket.connection;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BithumbExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
public class BithumbWebSocketTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private BithumbExchangeProtocol bithumbProtocol;

    @Test
    void bithumbWebSocketTest() {
        // Given
        String exchange = "bithumb";
        String url = "wss://pubwss.bithumb.com/pub/ws";
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );

        // When
        Flux<MessageHandler> connection = connectionFactory.createConnection(exchange, url);

        connection.flatMap(handler -> {
            log.info("Connected to Bithumb WebSocket");
            
            // Subscribe message 전송
            String subscribeMessage = bithumbProtocol.createSubscribeMessage(pairs);
            log.info("Sending subscribe message: {}", subscribeMessage);
            
            return handler.sendMessage(subscribeMessage)
                .thenMany(handler.receiveMessage()
                    .doOnNext(msg -> log.info("Received message: {}", msg))
                    .doOnError(error -> log.error("Error: {}", error.getMessage()))
                    .take(5)
                );
        })
        .doOnComplete(() -> log.info("Test completed"))
        .subscribe();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 