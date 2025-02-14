package com.example.boot.exchange.layer2_websocket.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.config.ExchangeConfig;
import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class BinanceMarketTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private BinanceExchangeProtocol binanceProtocol;

    @Autowired
    private ExchangeConfig exchangeConfig;

    @Test
    void testBinanceMarkets() {
        // Given
        String exchange = "binance";
        String url = exchangeConfig.getWebsocket().getBinance();
        
        // 테스트할 화폐쌍 직접 정의
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC"),  // USDT 마켓
            new CurrencyPair("USDT", "ETH"),  // USDT 마켓
            new CurrencyPair("BTC", "ETH")    // BTC 마켓
        );

        log.info("Testing Binance markets with pairs: {}", pairs);

        // When & Then
        connectionFactory.createConnection(exchange, url)
            .flatMap(handler -> {
                log.info("Connected to Binance WebSocket");
                
                String subscribeMessage = binanceProtocol.createSubscribeMessage(pairs);
                log.info("Sending subscribe message: {}", subscribeMessage);
                
                return handler.sendMessage(subscribeMessage)
                    .thenMany(handler.receiveMessage()
                        .doOnNext(msg -> {
                            try {
                                // 구독 응답 확인
                                if (msg.contains("\"result\"")) {
                                    log.info("Subscription response: {}", msg);
                                    // 구독 성공 확인
                                    assertThat(msg).contains("\"result\":null");
                                }
                                // 거래 데이터 확인
                                else if (msg.contains("\"e\":\"24hrTicker\"")) {
                                    JsonNode node = new ObjectMapper().readTree(msg);
                                    String symbol = node.get("s").asText();
                                    log.info("Received ticker for symbol: {}", symbol);
                                    
                                    // BTCUSDT, ETHUSDT, ETHBTC 중 하나인지 확인
                                    assertThat(symbol).matches("(BTC|ETH)USDT|ETHBTC");
                                    
                                    // 필수 필드 존재 확인
                                    assertThat(node.has("c")).isTrue(); // 현재가
                                    assertThat(node.has("v")).isTrue(); // 거래량
                                    assertThat(node.has("h")).isTrue(); // 고가
                                    assertThat(node.has("l")).isTrue(); // 저가
                                    assertThat(node.has("p")).isTrue(); // 가격 변화
                                    assertThat(node.has("P")).isTrue(); // 가격 변화율
                                }
                            } catch (JsonProcessingException e) {
                                log.error("JSON 파싱 에러: ", e);
                                fail("JSON 파싱 실패: " + e.getMessage());
                            }
                        })
                        // 구독 응답 메시지를 받은 후 첫 번째 거래 메시지만 확인
                        .takeUntil(msg -> msg.contains("\"e\":\"24hrTicker\""))
                    )
                    .doFinally(signalType -> {
                        handler.disconnect().subscribe();
                    });
            })
            .blockLast(Duration.ofSeconds(10));  // 5초 -> 10초로 증가
    }
} 