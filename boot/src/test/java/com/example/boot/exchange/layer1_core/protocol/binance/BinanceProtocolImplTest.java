package com.example.boot.exchange.layer1_core.protocol.binance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;

@SpringBootTest
class BinanceProtocolImplTest {
    private static final Logger log = LoggerFactory.getLogger(BinanceProtocolImplTest.class);

    @Autowired
    private BinanceExchangeProtocol protocol;
    
    // private String subscribeFormat;
    // private String unsubscribeFormat;
    
    // @BeforeEach
    // void setUp() {
    //     subscribeFormat = "{\"method\":\"SUBSCRIBE\",\"params\":[\"%s\"],\"id\":1}";
    //     unsubscribeFormat = "{\"method\":\"UNSUBSCRIBE\",\"params\":[\"%s\"],\"id\":1}";
    // }
    
    @Test
    @DisplayName("바이낸스 구독 메시지 포맷 테스트")
    void shouldCreateSubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC"),
            new CurrencyPair("USDT", "ETH")
        );
        
        // when
        String message = protocol.createSubscribeMessage(pairs);
        log.info("Binance subscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\",\"ethusdt@trade\"],\"id\":1}"
        );
    }
    
    @Test
    @DisplayName("바이낸스 구독 해제 메시지 포맷 테스트")
    void shouldCreateUnsubscribeMessage() {
        // given
        List<CurrencyPair> pairs = List.of(
            new CurrencyPair("USDT", "BTC"),
            new CurrencyPair("USDT", "ETH")
        );
        
        // when
        String message = protocol.createUnsubscribeMessage(pairs);
        log.info("Binance unsubscribe message: {}", message);
        
        // then
        assertThat(message).isEqualTo(
            "{\"method\":\"UNSUBSCRIBE\",\"params\":[\"btcusdt@trade\",\"ethusdt@trade\"],\"id\":1}"
        );
    }
    
    @Test
    @DisplayName("바이낸스 거래소 지원 여부 확인")
    void shouldSupportBinanceExchange() {
        assertThat(protocol.supports("binance")).isTrue();
        assertThat(protocol.supports("BINANCE")).isTrue();
        assertThat(protocol.supports("upbit")).isFalse();
    }
    
    @Test
    @DisplayName("거래소 이름 반환 테스트")
    void shouldReturnExchangeName() {
        assertThat(protocol.getExchangeName()).isEqualTo("binance");
    }
} 