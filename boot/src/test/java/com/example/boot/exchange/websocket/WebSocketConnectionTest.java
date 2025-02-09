package com.example.boot.exchange.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class WebSocketConnectionTest {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionTest.class);

    @Autowired
    private WebSocketManager webSocketManager;

    @Test
    void shouldConnectAndSubscribeToAllExchanges() throws InterruptedException {
        log.info("Starting WebSocket connection test...");
        
        // 1. WebSocket 연결 초기화
        webSocketManager.init();
        Thread.sleep(2000);  // 연결 대기
        
        // 2. 연결 상태 확인
        assertThat(webSocketManager.isSessionActive("binance")).isTrue();
        assertThat(webSocketManager.isSessionActive("upbit")).isTrue();
        assertThat(webSocketManager.isSessionActive("bithumb")).isTrue();
        
        // 3. 각 거래소별 구독
        log.info("Subscribing to Binance BTC/USDT...");
        webSocketManager.sendSubscribe("binance", "BTC");
        
        log.info("Subscribing to Upbit BTC/KRW...");
        webSocketManager.sendSubscribe("upbit", "BTC");
        
        log.info("Subscribing to Bithumb BTC/KRW...");
        webSocketManager.sendSubscribe("bithumb", "BTC");
        
        // 4. 구독 응답 대기 (좀 더 길게)
        Thread.sleep(5000);  // 5초로 증가
        
        // 5. 세션 상태 최종 확인
        assertThat(webSocketManager.getSession("binance").isOpen())
            .as("Binance session should be open")
            .isTrue();
            
        assertThat(webSocketManager.getSession("upbit").isOpen())
            .as("Upbit session should be open")
            .isTrue();
            
        assertThat(webSocketManager.getSession("bithumb").isOpen())
            .as("Bithumb session should be open")
            .isTrue();
            
        // 6. 로그 출력으로 실제 데이터 확인
        log.info("Test completed. Check the logs above for actual WebSocket messages.");
        log.info("Make sure you see:");
        log.info("1. Connection success messages for all exchanges");
        log.info("2. Subscription confirmation messages");
        log.info("3. Actual trade data messages");
    }
} 