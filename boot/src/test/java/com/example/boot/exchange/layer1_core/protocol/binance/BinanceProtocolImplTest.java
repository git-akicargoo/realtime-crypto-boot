package com.example.boot.exchange.layer1_core.protocol.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer1_core.protocol.BinanceExchangeProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
class BinanceProtocolImplTest {
    
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
        // ObjectMapper를 사용하여 JSON 문자열을 객체로 변환 후 다시 문자열로 변환하여 비교
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expectedNode = mapper.readTree(
                "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@ticker\",\"ethusdt@ticker\"],\"id\":1}"
            );
            JsonNode actualNode = mapper.readTree(message);
            
            assertThat(actualNode).isEqualTo(expectedNode);
        } catch (JsonProcessingException e) {
            fail("Failed to parse JSON", e);
        }
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
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expectedNode = mapper.readTree(
                "{\"method\":\"UNSUBSCRIBE\",\"params\":[\"btcusdt@ticker\",\"ethusdt@ticker\"],\"id\":1}"
            );
            JsonNode actualNode = mapper.readTree(message);
            
            assertThat(actualNode).isEqualTo(expectedNode);
        } catch (JsonProcessingException e) {
            fail("Failed to parse JSON", e);
        }
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