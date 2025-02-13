package com.example.boot.exchange.layer3_data_converter.integration;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

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
    void shouldConvertBinanceRealTimeData() throws InterruptedException {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC"),
            new CurrencyPair("USDT", "ETH")
        );

        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);

        log.info("[1] 테스트 시작");

        // when
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
                        
                        // 구독 응답 메시지 무시
                        if (rawMessage.contains("\"result\"")) {
                            log.info("[6-1] 구독 응답 메시지 수신");
                            return;
                        }

                        ExchangeMessage msg = new ExchangeMessage(
                            protocol.getExchangeName(),
                            rawMessage,
                            java.time.Instant.now(),
                            ExchangeMessage.MessageType.TICKER
                        );
                        log.info("[7] ExchangeMessage 생성 완료: {}", msg);
                        
                        converter.convert(msg)
                            .doOnNext(converted -> {
                                if (converted != null) {
                                    validateConvertedData(converted);
                                    log.info("[8] 변환 완료: {}", converted);
                                    successCount.incrementAndGet();
                                    latch.countDown();
                                }
                            })
                            .doOnError(error -> {
                                log.error("[9] 변환 중 에러 발생: ", error);
                                latch.countDown();
                            })
                            .subscribe();
                    })
                );
        })
        .subscribe(
            data -> log.info("[10] 데이터 처리 완료"),
            error -> {
                log.error("[11] 에러 발생: ", error);
                latch.countDown();
            },
            () -> log.info("[12] 스트림 완료")
        );

        // then
        log.info("[13] 메시지 대기 중...");
        if (!latch.await(15, TimeUnit.SECONDS)) {
            log.warn("[14] 타임아웃 발생");
        }
        
        log.info("[15] 테스트 종료 - 성공적으로 변환된 메시지 수: {}", successCount.get());
        assert successCount.get() > 0 : "최소 1개 이상의 메시지가 성공적으로 변환되어야 합니다.";
    }

    private void validateConvertedData(StandardExchangeData data) {
        assert data.getExchange().equals("binance") : "거래소 이름이 일치하지 않습니다.";
        assert data.getPrice() != null : "가격이 null입니다.";
        assert data.getVolume() != null : "거래량이 null입니다.";
        assert data.getHighPrice() != null : "고가가 null입니다.";
        assert data.getLowPrice() != null : "저가가 null입니다.";
        assert data.getPriceChange() != null : "변동가가 null입니다.";
        assert data.getPriceChangePercent() != null : "변동률이 null입니다.";
        assert data.getTimestamp() != null : "타임스탬프가 null입니다.";
    }
}