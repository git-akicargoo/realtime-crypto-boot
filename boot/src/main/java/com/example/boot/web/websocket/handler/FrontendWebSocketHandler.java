package com.example.boot.web.websocket.handler;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.common.session.model.ClientSession;
import com.example.boot.common.session.registry.SessionRegistry;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer4_distribution.common.factory.DistributionServiceFactory;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.direct.service.DirectDistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.service.KafkaDistributionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class FrontendWebSocketHandler extends TextWebSocketHandler {
    private final DistributionServiceFactory distributionServiceFactory;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public FrontendWebSocketHandler(
        DistributionServiceFactory distributionServiceFactory,
        SessionRegistry sessionRegistry,
        ObjectMapper objectMapper
    ) {
        this.distributionServiceFactory = distributionServiceFactory;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket connection established - Session ID: {}", sessionId);
        
        try {
            // 1. 세션 등록
            ClientSession clientSession = ClientSession.builder()
                .sessionId(sessionId)
                .clientId(sessionId)
                .sessionType(ClientSession.SessionType.WEBSOCKET)
                .session(session)
                .build();
            sessionRegistry.registerSession(sessionId, clientSession);
            
            // 2. 현재 서비스에 Sink 생성 및 등록
            DistributionService currentService = distributionServiceFactory.getCurrentService();
            if (currentService != null) {
                Sinks.Many<StandardExchangeData> sink = Sinks.many().multicast().onBackpressureBuffer();
                
                // 대신 DirectDistributionService의 clientSinks에 직접 추가해야 합니다
                if (currentService instanceof DirectDistributionService) {
                    ((DirectDistributionService) currentService).addClientSink(sessionId, sink);
                } else if (currentService instanceof KafkaDistributionService) {
                    ((KafkaDistributionService) currentService).addClientSink(sessionId, sink);
                }
                
                // 3. Sink를 통해 데이터 수신 및 클라이언트로 전송
                sink.asFlux()
                    .doOnNext(data -> {
                        try {
                            String jsonData = objectMapper.writeValueAsString(data);
                            session.sendMessage(new TextMessage(jsonData));
                        } catch (Exception e) {
                            log.error("Error sending message to client: {}", e.getMessage());
                        }
                    })
                    .subscribe();
                
                log.info("Client sink created and registered - Session ID: {}", sessionId);
            } else {
                log.warn("No active distribution service found");
            }
        } catch (Exception e) {
            log.error("Error during WebSocket connection setup: {}", e.getMessage());
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ex) {
                log.error("Error closing WebSocket session", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        log.info("WebSocket connection closed - Session ID: {}, Status: {}", sessionId, status);
        
        // 1. 세션 제거
        sessionRegistry.removeSession(sessionId);
        
        // 2. Sink 제거
        DistributionService currentService = distributionServiceFactory.getCurrentService();
        if (currentService != null) {
            Map<String, Sinks.Many<StandardExchangeData>> sinks = currentService.getActiveSinks();
            sinks.remove(sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Received message from client {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.error("Error closing session after transport error", e);
        }
    }
} 