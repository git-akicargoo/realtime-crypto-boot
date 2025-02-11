package com.example.boot.exchange.layer1_core.protocol.upbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.UpbitExchangeProtocol;

@SpringBootTest
class UpbitProtocolImplTest {
    private static final Logger log = LoggerFactory.getLogger(UpbitProtocolImplTest.class);

    @Autowired
    private UpbitExchangeProtocol protocol;
    
    private String subscribeFormat;
    private String unsubscribeFormat;
    
    @BeforeEach
    void setUp() {
        subscribeFormat = "[{\"ticket\":\"UNIQUE_TICKET\"},{\"type\":\"trade\",\"codes\":[\"%s\"]}]";
        unsubscribeFormat = "[{\"ticket\":\"UNIQUE_TICKET\"},{\"type\":\"trade\",\"codes\":[\"%s\"]}]";
    }
    
    @Test
    @DisplayName("업비트 구독 메시지 포맷 테스트")
    void shouldCreateSubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );
        
        // when
        String message = protocol.createSubscribeMessage(pairs, null);
        log.info("Upbit subscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "[{\"ticket\":\"UNIQUE_TICKET\"},{\"type\":\"trade\",\"codes\":[\"KRW-BTC\",\"KRW-ETH\"]},{\"format\":\"SIMPLE\"}]"
        );
    }
    
    @Test
    @DisplayName("업비트 구독 해제 메시지 포맷 테스트")
    void shouldCreateUnsubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("KRW", "BTC"),
            new CurrencyPair("KRW", "ETH")
        );
        
        // when
        String message = protocol.createUnsubscribeMessage(pairs, unsubscribeFormat);
        log.info("Upbit unsubscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "[{\"ticket\":\"UNIQUE_TICKET\"},{\"type\":\"trade\",\"codes\":[\"KRW-BTC\",\"KRW-ETH\"]}]"
        );
    }
    
    @Test
    @DisplayName("업비트 거래소 지원 여부 확인")
    void shouldSupportUpbitExchange() {
        assertThat(protocol.supports("upbit")).isTrue();
        assertThat(protocol.supports("UPBIT")).isTrue();
        assertThat(protocol.supports("binance")).isFalse();
    }
    
    @Test
    @DisplayName("거래소 이름 반환 테스트")
    void shouldReturnExchangeName() {
        assertThat(protocol.getExchangeName()).isEqualTo("upbit");
    }
} 