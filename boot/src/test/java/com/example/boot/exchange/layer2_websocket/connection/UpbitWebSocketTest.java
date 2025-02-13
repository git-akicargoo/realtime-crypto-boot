package com.example.boot.exchange.layer2_websocket.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.UpbitExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
public class UpbitWebSocketTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private UpbitExchangeProtocol upbitProtocol;

    @Test
    void upbitWebSocketTest() {
        // Given
        String exchange = "upbit";
        String url = "wss://api.upbit.com/websocket/v1";
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );

        // When
        Flux<MessageHandler> connection = connectionFactory.createConnection(exchange, url);

        connection.flatMap(handler -> {
            log.info("Connected to Upbit WebSocket");
            
            // Subscribe message 전송 (바이너리로 전송)
            String subscribeMessage = upbitProtocol.createSubscribeMessage(pairs);
            log.info("Sending subscribe message: {}", subscribeMessage);
            
            // 업비트는 바이너리 메시지로 전송
            byte[] binaryMessage = subscribeMessage.getBytes();

            return handler.sendBinaryMessage(binaryMessage)
                .thenMany(handler.receiveMessage()
                    .doOnNext(msg -> {
                        log.info("Received message: {}", msg);
                        // ticker 데이터 검증 로직 추가
                        if (msg.contains("\"type\":\"ticker\"")) {
                            JsonNode node;
                            try {
                                node = new ObjectMapper().readTree(msg);
                                String code = node.get("code").asText();
                                String price = node.get("trade_price").asText();
                                log.info("Ticker data - Code: {}, Price: {}", code, price);
                                
                                assertThat(node.has("type")).isTrue();
                                assertThat(node.has("code")).isTrue();
                                assertThat(node.has("trade_price")).isTrue();
                            } catch (Exception e) {
                                log.error("Error parsing message: {}", e.getMessage());
                                fail("Failed to parse ticker message");
                            }
                        }
                    })
                    .take(5)
                );
        })
        .doOnComplete(() -> log.info("Test completed"))
        .subscribe();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 