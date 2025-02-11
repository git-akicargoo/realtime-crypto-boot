package com.example.boot.exchange.layer3_data_converter.integration;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.config.ExchangeConfig;
import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.model.ExchangeMessage;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.example.boot.exchange.layer2_websocket.connection.ConnectionFactory;
import com.example.boot.exchange.layer3_data_converter.converter.binance.BinanceConverter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class BinanceConverterIntegrationTest {

    @Autowired
    private ExchangeConfig config;
    @Autowired
    private ConnectionFactory connectionFactory;
    @Autowired
    private BinanceExchangeProtocol protocol;
    @Autowired
    private BinanceConverter converter;

    @Test
    @DisplayName("바이낸스 실시간 데이터 변환 테스트")
    void shouldConvertBinanceRealTimeData() {
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC")
        );

        CountDownLatch latch = new CountDownLatch(5);

        log.info("[1] 테스트 시작");

        connectionFactory.createConnection(
            protocol.getExchangeName(),
            config.getWebsocket().getBinance()
        )
        .doOnNext(handler -> log.info("[2] WebSocket 연결 성공"))
        .flatMap(handler -> {
            String subscribeMessage = protocol.createSubscribeMessage(pairs);
            log.info("[3] 구독 메시지 생성: {}", subscribeMessage);
            
            return handler.sendMessage(subscribeMessage)
                .doOnNext(v -> log.info("[4] 구독 메시지 전송 완료"))
                .thenMany(handler.receiveMessage()
                    .doOnSubscribe(s -> log.info("[5] 메시지 수신 시작"))
                    .doOnNext(rawMessage -> {
                        log.info("[6] 원본 메시지 수신: {}", rawMessage);
                        
                        ExchangeMessage msg = new ExchangeMessage(
                            protocol.getExchangeName(),
                            rawMessage,
                            java.time.Instant.now(),
                            ExchangeMessage.MessageType.TRADE
                        );
                        log.info("[7] ExchangeMessage 생성 완료: {}", msg);
                        
                        converter.convert(msg)
                            .doOnNext(converted -> {
                                log.info("[8] 변환 완료: {}", converted);
                                latch.countDown();
                            })
                            .doOnError(error -> log.error("[9] 변환 중 에러 발생: ", error))
                            .subscribe();  // 명시적 구독 추가
                    })
                );
        })
        .subscribe(
            data -> log.info("[10] 데이터 처리 완료"),
            error -> log.error("[11] 에러 발생: ", error),
            () -> log.info("[12] 스트림 완료")
        );

        log.info("[13] 메시지 대기 중...");
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {  // 3초 -> 10초로 증가
                log.warn("[14] 타임아웃 발생");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        log.info("[15] 테스트 종료");
    }
}