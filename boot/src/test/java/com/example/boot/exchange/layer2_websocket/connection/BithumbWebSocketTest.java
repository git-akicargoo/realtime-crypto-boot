package com.example.boot.exchange.layer2_websocket.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BithumbExchangeProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class BithumbWebSocketTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private BithumbExchangeProtocol bithumbProtocol;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("빗썸 웹소켓 연결 및 데이터 수신 테스트")
    void bithumbWebSocketTest() throws InterruptedException {
        // Given
        String exchange = "bithumb";
        String url = "wss://pubwss.bithumb.com/pub/ws";
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC")
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean receivedTicker = new AtomicBoolean(false);

        // When
        connectionFactory.createConnection(exchange, url)
            .flatMap(handler -> {
                log.info("[1] Connected to Bithumb WebSocket");
                
                String subscribeMessage = bithumbProtocol.createSubscribeMessage(pairs);
                log.info("[2] Sending ticker subscribe message: {}", subscribeMessage);
                
                return handler.sendMessage(subscribeMessage)
                    .thenMany(handler.receiveMessage()
                        .doOnNext(msg -> {
                            log.info("[3] Received message: {}", msg);
                            try {
                                JsonNode node = objectMapper.readTree(msg);
                                if (node.has("type") && "ticker".equals(node.get("type").asText())) {
                                    JsonNode content = node.get("content");
                                    if (content != null) {
                                        log.info("[4] Ticker data received: closePrice={}, volume={}",
                                            content.get("closePrice").asText(),
                                            content.get("volume").asText()
                                        );
                                        receivedTicker.set(true);
                                        latch.countDown();
                                    }
                                }
                            } catch (JsonProcessingException e) {
                                log.error("[5] Error parsing JSON message: {}", e.getMessage());
                            }
                        })
                        .doOnError(error -> {
                            log.error("[6] Error in message stream: {}", error.getMessage());
                            latch.countDown();
                        })
                    );
            })
            .subscribe();

        // Then
        if (!latch.await(30, TimeUnit.SECONDS)) {
            log.warn("[7] Timeout waiting for messages");
        }
        
        assertThat(receivedTicker.get())
            .withFailMessage("No ticker data received from Bithumb")
            .isTrue();
    }
} 