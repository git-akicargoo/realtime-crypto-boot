package com.example.boot.exchange.layer1_core.protocol.bithumb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BithumbExchangeProtocol;

@SpringBootTest
class BithumbProtocolImplTest {
    private static final Logger log = LoggerFactory.getLogger(BithumbProtocolImplTest.class);

    @Autowired
    private BithumbExchangeProtocol protocol;
    
    @Test
    @DisplayName("빗썸 구독 메시지 포맷 테스트")
    void shouldCreateSubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );
        
        // when
        String message = protocol.createSubscribeMessage(pairs);
        log.info("Bithumb subscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "{\"type\":\"ticker\",\"symbols\":[\"BTC_KRW\",\"ETH_KRW\"],\"tickTypes\":[\"24H\"]}"
        );
    }
    
    @Test
    @DisplayName("빗썸 구독 해제 메시지 포맷 테스트")
    void shouldCreateUnsubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );
        
        // when
        String message = protocol.createUnsubscribeMessage(pairs);
        log.info("Bithumb unsubscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "{\"type\":\"ticker\",\"symbols\":[\"BTC_KRW\",\"ETH_KRW\"],\"tickTypes\":[\"24H\"]}"
        );
    }
    
    @Test
    @DisplayName("빗썸 거래소 지원 여부 확인")
    void shouldSupportBithumbExchange() {
        assertThat(protocol.supports("bithumb")).isTrue();
        assertThat(protocol.supports("BITHUMB")).isTrue();
        assertThat(protocol.supports("binance")).isFalse();
    }
    
    @Test
    @DisplayName("거래소 이름 반환 테스트")
    void shouldReturnExchangeName() {
        assertThat(protocol.getExchangeName()).isEqualTo("bithumb");
    }
} 