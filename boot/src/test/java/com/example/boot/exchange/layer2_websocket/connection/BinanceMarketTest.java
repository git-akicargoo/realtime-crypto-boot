package com.example.boot.exchange.layer2_websocket.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.config.ExchangeConfig;
import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
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
        
        List<String> symbols = exchangeConfig.getCommon().getSupportedSymbols();
        List<String> currencies = exchangeConfig.getExchanges().getBinance().getSupportedCurrencies();
        
        List<CurrencyPair> pairs = symbols.stream()
            .flatMap(symbol -> 
                currencies.stream()
                    .map(currency -> new CurrencyPair(currency, symbol))
            )
            .collect(Collectors.toList());
            
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
                            // 구독 응답 확인
                            if (msg.contains("\"result\"")) {
                                log.info("Subscription response: {}", msg);
                                // 구독 성공 확인
                                assertThat(msg).contains("\"result\":null");
                            }
                            // 거래 데이터 확인
                            else if (msg.contains("\"e\":\"24hrTicker\"")) {
                                JsonNode node;
                                try {
                                    node = new ObjectMapper().readTree(msg);
                                    String symbol = node.get("s").asText();
                                    String price = node.get("c").asText();      // 현재가
                                    String high = node.get("h").asText();       // 고가
                                    String low = node.get("l").asText();        // 저가
                                    String priceChange = node.get("p").asText(); // 가격 변화
                                    String priceChangePercent = node.get("P").asText(); // 변화율
                                    String volume = node.get("v").asText();      // 거래량

                                    log.info("Ticker data for {}: \n" +
                                        "  Current Price: {} \n" +
                                        "  24h High: {} \n" +
                                        "  24h Low: {} \n" +
                                        "  24h Change: {} ({} %) \n" +
                                        "  24h Volume: {}", 
                                        symbol, price, high, low, priceChange, priceChangePercent, volume);
                                    
                                    // 메시지 형식 검증
                                    assertThat(node.has("e")).isTrue();
                                    assertThat(node.has("s")).isTrue();
                                    assertThat(node.has("c")).isTrue();
                                    assertThat(node.has("h")).isTrue();
                                    assertThat(node.has("l")).isTrue();
                                    assertThat(node.has("p")).isTrue();
                                    assertThat(node.has("P")).isTrue();
                                    assertThat(node.has("v")).isTrue();
                                } catch (Exception e) {
                                    log.error("Error parsing message: {}", e.getMessage());
                                    fail("Failed to parse ticker message");
                                }
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