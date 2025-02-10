package com.example.boot.exchange_layer.layer3_exchange_protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.exchange_layer.layer1_websocket_manager.connection.DefaultWebSocketConnector;
import com.example.boot.exchange_layer.layer3_exchange_protocol.model.CurrencyPair;

import lombok.extern.slf4j.Slf4j;

/**
 * 거래소별 WebSocket 프로토콜 메시지 생성 테스트
 * 
 * 테스트 실행 방법:
 * ./gradlew test --tests "com.example.boot.exchange_layer.layer3_exchange_protocol.ExchangeProtocolTests" --info
 */
@Slf4j
@SpringBootTest(
    properties = {
        "spring.kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@ActiveProfiles("local")
@ComponentScan(
    basePackages = "com.example.boot.exchange_layer",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {DefaultWebSocketConnector.class}
    )
)
class ExchangeProtocolTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public WebSocketClient webSocketClient() {
            return new ReactorNettyWebSocketClient();
        }
    }

    @Autowired
    private List<ExchangeProtocol> protocols;
    
    // 각 거래소별 구독 메시지 포맷 주입
    @Value("${exchange.message-format.binance.subscribe}")
    private String binanceSubscribeFormat;
    
    @Value("${exchange.message-format.upbit.subscribe}")
    private String upbitSubscribeFormat;
    
    @Value("${exchange.message-format.bithumb.subscribe}")
    private String bithumbSubscribeFormat;

    @Test
    @DisplayName("바이낸스 프로토콜 메시지 포맷 테스트")
    void testBinanceProtocol() {
        // Given
        ExchangeProtocol binanceProtocol = findProtocol("binance");
        CurrencyPair pair = new CurrencyPair("USDT", "BTC");
        
        // When
        String message = binanceProtocol.createSubscribeMessage(
            List.of(pair), 
            binanceSubscribeFormat
        );
        
        // Then
        log.info("Binance subscribe message: {}", message);
        assertThat(message)
            .isNotNull()
            .isNotBlank()
            .contains("btcusdt@trade")
            .contains("SUBSCRIBE")
            .doesNotContain("\"\"")  // 중복 따옴표 체크
            .doesNotContain("@trade@trade")  // @trade 중복 체크
            .matches("\\{.*\\}");    // JSON 형식 체크
    }
    
    @Test
    @DisplayName("업비트 프로토콜 메시지 포맷 테스트")
    void testUpbitProtocol() {
        // Given
        ExchangeProtocol upbitProtocol = findProtocol("upbit");
        CurrencyPair pair = new CurrencyPair("KRW", "BTC");
        
        // When
        String message = upbitProtocol.createSubscribeMessage(
            List.of(pair), 
            upbitSubscribeFormat
        );
        
        // Then
        log.info("Upbit subscribe message: {}", message);
        assertThat(message)
            .isNotNull()
            .isNotBlank()
            .contains("KRW-BTC")
            .contains("SIMPLE")
            .doesNotContain("\"\"")  // 중복 따옴표 체크
            .matches("\\[.*\\]");    // JSON 배열 형식 체크
    }
    
    @Test
    @DisplayName("빗썸 프로토콜 메시지 포맷 테스트")
    void testBithumbProtocol() {
        // Given
        ExchangeProtocol bithumbProtocol = findProtocol("bithumb");
        CurrencyPair pair = new CurrencyPair("KRW", "BTC");
        
        // When
        String message = bithumbProtocol.createSubscribeMessage(
            List.of(pair), 
            bithumbSubscribeFormat
        );
        
        // Then
        log.info("Bithumb subscribe message: {}", message);
        assertThat(message)
            .isNotNull()
            .isNotBlank()
            .contains("BTC_KRW")
            .contains("transaction")
            .doesNotContain("\"\"")  // 중복 따옴표 체크
            .matches("\\{.*\\}");    // JSON 형식 체크
    }
    
    /**
     * 거래소명으로 해당 프로토콜 구현체를 찾는 헬퍼 메서드
     */
    private ExchangeProtocol findProtocol(String exchange) {
        return protocols.stream()
            .filter(p -> p.supports(exchange))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Protocol not found: " + exchange));
    }
} 