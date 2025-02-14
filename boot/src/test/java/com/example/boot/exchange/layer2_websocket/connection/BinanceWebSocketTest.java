package com.example.boot.exchange.layer2_websocket.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.handler.MessageHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            new CurrencyPair("USDT", "ETH"),
            new CurrencyPair("BTC", "ETH")
        );

        // When
        Flux<MessageHandler> connection = connectionFactory.createConnection(exchange, url);

        connection.flatMap(handler -> {
            log.info("Connected to Binance WebSocket");
            
            String subscribeMessage = binanceProtocol.createSubscribeMessage(pairs);
            log.info("Sending subscribe message: {}", subscribeMessage);
            
            return handler.sendMessage(subscribeMessage)
                .thenMany(handler.receiveMessage()
                    .doOnNext(msg -> {
                        try {
                            if (msg.contains("\"result\"")) {
                                log.info("Subscription response: {}", msg);
                                assertThat(msg).contains("\"result\":null");
                            }
                            // 티커 데이터 확인
                            else if (msg.contains("\"e\":\"24hrTicker\"")) {
                                JsonNode node = new ObjectMapper().readTree(msg);
                                String symbol = node.get("s").asText();
                                String price = node.get("c").asText();
                                log.info("Received ticker - Symbol: {}, Price: {}", symbol, price);
                                
                                // 심볼이 우리가 구독한 페어 중 하나인지 확인
                                assertThat(symbol).matches("(BTC|ETH)USDT|ETHBTC");
                            }
                        } catch (JsonProcessingException e) {
                            log.error("JSON 파싱 에러: ", e);
                            fail("JSON 파싱 실패: " + e.getMessage());
                        }
                    })
                    .doOnError(error -> log.error("Error: {}", error.getMessage()))
                    .take(5) // 5개의 메시지만 받고 종료
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