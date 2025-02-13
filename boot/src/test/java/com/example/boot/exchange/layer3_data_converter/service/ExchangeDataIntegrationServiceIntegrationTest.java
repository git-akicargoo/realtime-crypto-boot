package com.example.boot.exchange.layer3_data_converter.service;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest
class ExchangeDataIntegrationServiceIntegrationTest {

    @Autowired
    private ExchangeDataIntegrationService integrationService;

    @Test
    void subscribe_ShouldStreamRealTimeData() {
        // when
        Flux<StandardExchangeData> dataStream = integrationService.subscribe()
            .take(Duration.ofSeconds(10))
            .doOnSubscribe(s -> log.info("Subscription started"))
            .doOnNext(data -> 
                log.info("Received exchange data - Exchange: {}, Pair: {}, Price: {}", 
                    data.getExchange(), 
                    data.getCurrencyPair(), 
                    data.getPrice())
            )
            .doOnComplete(() -> log.info("Stream completed"))
            .doOnError(e -> log.error("Error occurred in stream: ", e))
            .doOnCancel(() -> log.info("Stream was cancelled"));

        // then
        StepVerifier.create(dataStream)
            .expectSubscription()
            .thenAwait(Duration.ofSeconds(10))
            .thenConsumeWhile(data -> true)
            .verifyComplete();
    }
} 