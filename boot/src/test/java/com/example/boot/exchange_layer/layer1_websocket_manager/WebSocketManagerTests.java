package com.example.boot.exchange_layer.layer1_websocket_manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.exchange_layer.layer1_websocket_manager.connection.WebSocketConnector;
import com.example.boot.exchange_layer.layer1_websocket_manager.session.SessionManager;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(
    properties = {
        "spring.kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@ActiveProfiles("local")
class WebSocketManagerTests {

    @MockBean
    private WebSocketClient webSocketClient;

    @Autowired
    private WebSocketConnector webSocketConnector;

    @Autowired
    private SessionManager sessionManager;

    @Test
    @DisplayName("WebSocket 연결 테스트")
    void testWebSocketConnection() {
        // Given
        String exchange = "binance";
        String url = "wss://stream.binance.com:9443/ws";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.receive()).thenReturn(Flux.empty());
        when(mockSession.send(any())).thenReturn(Mono.empty());
        when(webSocketClient.execute(any(), any())).thenReturn(Mono.just(mockSession));

        // When
        Mono<Void> connection = webSocketConnector.connect(exchange, url);

        // Then
        StepVerifier.create(connection)
            .verifyComplete();

        assertThat(sessionManager.hasSession(exchange)).isTrue();
    }

    @Test
    @DisplayName("WebSocket 메시지 전송 테스트")
    void testSendMessage() {
        // Given
        String exchange = "binance";
        String message = "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.receive()).thenReturn(Flux.empty());
        when(mockSession.send(any())).thenReturn(Mono.empty());
        when(webSocketClient.execute(any(), any())).thenReturn(Mono.just(mockSession));
        webSocketConnector.connect(exchange, "wss://stream.binance.com:9443/ws").block();

        // When
        Mono<Void> sendResult = webSocketConnector.sendTextMessage(exchange, message);

        // Then
        StepVerifier.create(sendResult)
            .verifyComplete();
        verify(mockSession).send(any());
    }

    @Test
    @DisplayName("WebSocket 세션 관리 테스트")
    void testSessionManagement() {
        // Given
        String exchange = "binance";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.receive()).thenReturn(Flux.empty());
        when(mockSession.send(any())).thenReturn(Mono.empty());
        when(webSocketClient.execute(any(), any())).thenReturn(Mono.just(mockSession));

        // When
        webSocketConnector.connect(exchange, "wss://stream.binance.com:9443/ws").block();

        // Then
        assertThat(sessionManager.hasSession(exchange)).isTrue();

        // When
        sessionManager.removeSession(exchange).block();

        // Then
        assertThat(sessionManager.hasSession(exchange)).isFalse();
    }

    @Test
    @DisplayName("모든 WebSocket 세션 제거 테스트")
    void testRemoveAllSessions() {
        // Given
        String[] exchanges = {"binance", "upbit", "bithumb"};
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.receive()).thenReturn(Flux.empty());
        when(mockSession.send(any())).thenReturn(Mono.empty());
        when(webSocketClient.execute(any(), any())).thenReturn(Mono.just(mockSession));

        // When - 여러 거래소 연결
        for (String exchange : exchanges) {
            webSocketConnector.connect(exchange, "wss://" + exchange + ".com/ws").block();
        }

        // Then
        for (String exchange : exchanges) {
            assertThat(sessionManager.hasSession(exchange)).isTrue();
        }

        // When - 모든 세션 제거
        sessionManager.removeAllSessions().block();

        // Then
        for (String exchange : exchanges) {
            assertThat(sessionManager.hasSession(exchange)).isFalse();
        }
    }
} 