package com.example.boot.exchange.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.example.boot.config.ExchangeConfigVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class StandaloneWebSocketManager extends AbstractWebSocketManager {
    
    public StandaloneWebSocketManager(ExchangeConfigVO config, WebSocketClient client) {
        super(config, client);
    }

    @Override
    public boolean isLeader() {
        return true;  // 단독 실행 모드에서는 항상 리더
    }

    @Override
    public void tryBecomeLeader() {
        log.info("Running in standalone mode - always leader");
    }

    @Override
    public void handleExchangeData(String exchange, String data) {
        log.info("Processing data from {}: {}", exchange, data);
        // TODO: 실제 데이터 처리 로직 구현
    }
} 