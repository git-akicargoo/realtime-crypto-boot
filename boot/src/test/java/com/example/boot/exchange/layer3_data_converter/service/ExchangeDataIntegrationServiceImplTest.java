package com.example.boot.exchange.layer3_data_converter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;

import lombok.extern.slf4j.Slf4j;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest
class ExchangeDataIntegrationServiceImplTest {

    private static final StringBuilder finalResultBuilder = new StringBuilder();
    private static final Map<String, Boolean> testResults = new ConcurrentHashMap<>();
    private static final int TIMEOUT_SECONDS = 30;  // 타임아웃 시간을 30초로 증가

    @Autowired
    private ExchangeDataIntegrationService service;

    @Test
    @Order(1)
    @DisplayName("여러 거래소 동시 구독 테스트")
    void shouldSubscribeToMultipleExchanges() throws InterruptedException {
        try {
            // given
            Map<String, List<CurrencyPair>> exchangePairs = new HashMap<>();
            exchangePairs.put("binance", List.of(
                new CurrencyPair("USDT", "BTC"),
                new CurrencyPair("USDT", "ETH"),
                new CurrencyPair("BTC", "ETH")
            ));
            exchangePairs.put("upbit", List.of(new CurrencyPair("KRW", "BTC")));
            exchangePairs.put("bithumb", List.of(new CurrencyPair("KRW", "BTC")));

            Map<String, Boolean> receivedData = new ConcurrentHashMap<>();
            exchangePairs.keySet().forEach(exchange -> receivedData.put(exchange, false));
            
            CountDownLatch latch = new CountDownLatch(exchangePairs.size());
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("\n=== 거래소 실시간 데이터 구독 테스트 결과 ===\n");
            
            // when
            service.subscribe(exchangePairs)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .doOnNext(data -> {
                    String exchange = data.getExchange();
                    if (!receivedData.get(exchange)) {  // 첫 데이터만 상세 로깅
                        receivedData.put(exchange, true);
                        resultBuilder.append(String.format("""
                            [%s] 데이터 수신 성공
                            - 화폐쌍: %s
                            - 가격: %s
                            - 수량: %s
                            - 시간: %s
                            """, 
                            exchange,
                            data.getCurrencyPair(),
                            data.getPrice(),
                            data.getVolume(),
                            data.getTimestamp()
                        ));
                        log.info("거래소 {} 데이터 수신 성공", exchange);
                        latch.countDown();
                    } else {
                        // 이후 데이터는 간단히 로깅
                        log.debug("추가 데이터 수신: {}", exchange);
                    }
                })
                .subscribe();

            // then
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            resultBuilder.append("\n=== 테스트 결과 요약 ===\n");
            
            receivedData.forEach((exchange, received) -> {
                resultBuilder.append(String.format("%s: %s\n", 
                    exchange, 
                    received ? "✅ 성공" : "❌ 실패"
                ));
            });
            
            log.info(resultBuilder.toString());
            
            assertThat(completed).withFailMessage("일부 거래소에서 데이터를 받지 못했습니다").isTrue();
            receivedData.forEach((exchange, received) -> {
                assertThat(received)
                    .withFailMessage("거래소 " + exchange + "에서 데이터를 받지 못했습니다")
                    .isTrue();
            });
            
            testResults.put("동시 구독 테스트", true);
        } catch (Exception e) {
            testResults.put("동시 구독 테스트", false);
            log.error("테스트 실패: ", e);
            throw e;
        }
    }

    @Test
    @DisplayName("모든 구독 해지 테스트")
    void shouldUnsubscribeAllSuccessfully() {
        try {
            Map<String, List<CurrencyPair>> exchangePairs = new HashMap<>();
            exchangePairs.put("binance", List.of(new CurrencyPair("USDT", "BTC")));
            exchangePairs.put("upbit", List.of(new CurrencyPair("KRW", "BTC")));
            exchangePairs.put("bithumb", List.of(new CurrencyPair("KRW", "BTC")));

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("\n=== 모든 구독 해지 테스트 ===\n");
            
            Map<String, Boolean> receivedData = new ConcurrentHashMap<>();
            exchangePairs.keySet().forEach(exchange -> receivedData.put(exchange, false));

            StepVerifier.create(
                service.subscribe(exchangePairs)
                    .doOnNext(data -> {
                        String exchange = data.getExchange();
                        if (!receivedData.get(exchange)) {
                            receivedData.put(exchange, true);
                            resultBuilder.append(String.format("""
                                [%s] 데이터 수신
                                - 화폐쌍: %s
                                - 가격: %s
                                - 수량: %s
                                """,
                                exchange,
                                data.getCurrencyPair(),
                                data.getPrice(),
                                data.getVolume()
                            ));
                            log.info("데이터 수신 확인 - {}", exchange);
                        }
                    })
                    .take(exchangePairs.size())
                    .then(service.unsubscribeAll()
                        .doFinally(signalType -> {
                            resultBuilder.append("\n=== 구독 해지 결과 ===\n");
                            boolean allReceived = true;
                            for (Map.Entry<String, Boolean> entry : receivedData.entrySet()) {
                                String exchange = entry.getKey();
                                boolean received = entry.getValue();
                                resultBuilder.append(String.format("%s: %s\n", 
                                    exchange, 
                                    received ? "✅ 데이터 수신 및 구독 해지 완료" : "❌ 데이터 미수신"
                                ));
                                if (!received) {
                                    allReceived = false;
                                }
                            }
                            log.info(resultBuilder.toString());
                            
                            if (!allReceived) {
                                throw new AssertionError("일부 거래소에서 데이터를 받지 못했습니다: " + receivedData);
                            }
                        }))
            )
            .expectComplete()
            .verify(Duration.ofSeconds(TIMEOUT_SECONDS));

            testResults.put("모든 구독 해지 테스트", true);
        } catch (Throwable e) {
            log.error("테스트 실패: {}", e.getMessage());
            testResults.put("모든 구독 해지 테스트", false);
            throw e;
        }
    }

    @Test
    @DisplayName("바이낸스 구독 해지 테스트")
    void shouldUnsubscribeBinanceSuccessfully() {
        try {
            testSingleExchangeUnsubscribe("binance", new CurrencyPair("USDT", "BTC"));
            testResults.put("바이낸스 구독 해지 테스트", true);
        } catch (Throwable e) {  // Exception 대신 Throwable로 변경
            testResults.put("바이낸스 구독 해지 테스트", false);
            log.error("바이낸스 구독 해지 테스트 실패: ", e);
            throw e;
        }
    }

    @Test
    @DisplayName("업비트 구독 해지 테스트")
    void shouldUnsubscribeUpbitSuccessfully() {
        try {
            testSingleExchangeUnsubscribe("upbit", new CurrencyPair("KRW", "BTC"));
            testResults.put("업비트 구독 해지 테스트", true);
        } catch (Exception e) {
            testResults.put("업비트 구독 해지 테스트", false);
            throw e;
        }
    }

    @Test
    @DisplayName("빗썸 구독 해지 테스트")
    void shouldUnsubscribeBithumbSuccessfully() {
        try {
            testSingleExchangeUnsubscribe("bithumb", new CurrencyPair("KRW", "BTC"));
            testResults.put("빗썸 구독 해지 테스트", true);
        } catch (Exception e) {
            testResults.put("빗썸 구독 해지 테스트", false);
            throw e;
        }
    }

    private void testSingleExchangeUnsubscribe(String exchange, CurrencyPair pair) {
        Map<String, List<CurrencyPair>> exchangePairs = new HashMap<>();
        exchangePairs.put(exchange, List.of(pair));

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(String.format("\n=== %s 구독 해지 테스트 ===\n", exchange));

        StepVerifier.create(
            service.subscribe(exchangePairs)
                .take(1)
                .doOnNext(data -> {
                    resultBuilder.append(String.format("""
                        [%s] 데이터 수신
                        - 화폐쌍: %s
                        - 가격: %s
                        - 수량: %s
                        - 시간: %s
                        """,
                        data.getExchange(),
                        data.getCurrencyPair(),
                        data.getPrice(),
                        data.getVolume(),
                        data.getTimestamp()
                    ));
                })
                .then(service.unsubscribe(exchange)
                    .doFinally(signalType -> {
                        resultBuilder.append(String.format("✅ %s 구독 해지 완료\n", exchange));
                        log.info(resultBuilder.toString());
                    }))
        )
        .expectComplete()
        .verify(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    @AfterAll
    static void printFinalResults() {
        finalResultBuilder.append("\n\n");
        finalResultBuilder.append("┌─────────────────────────────────────────────\n");
        finalResultBuilder.append("│ 거래소 데이터 통합 테스트 최종 결과\n");
        finalResultBuilder.append("├─────────────────────────────────────────────\n");
        
        boolean allSuccess = true;
        for (Map.Entry<String, Boolean> result : testResults.entrySet()) {
            String testName = result.getKey();
            boolean success = result.getValue();
            allSuccess &= success;
            
            finalResultBuilder.append(String.format("│ %s: %s\n", 
                testName, 
                success ? "✅ 성공" : "❌ 실패"
            ));
        }
        
        finalResultBuilder.append("├─────────────────────────────────────────────\n");
        finalResultBuilder.append(String.format("│ 최종 결과: %s\n", 
            allSuccess ? "✅ 모든 테스트 성공" : "❌ 일부 테스트 실패"
        ));
        finalResultBuilder.append("└─────────────────────────────────────────────\n");
        
        log.info(finalResultBuilder.toString());
    }
} 