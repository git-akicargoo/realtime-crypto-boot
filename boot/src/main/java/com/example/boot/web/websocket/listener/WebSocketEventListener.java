package com.example.boot.web.websocket.listener;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisManager;
import com.example.boot.exchange.layer6_analysis.session_analysis.AnalysisSessionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 이벤트 리스너
 * WebSocket 세션 연결 및 종료 이벤트를 감지하여 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    
    private final AnalysisSessionRegistry sessionRegistry;
    private final AnalysisManager analysisManager;
    
    /**
     * 세션 연결 이벤트 처리
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("Received a new web socket connection, sessionId: {}", sessionId);
        sessionRegistry.registerSession(sessionId);
    }
    
    /**
     * 세션 종료 이벤트 처리
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("User disconnected, sessionId: {}", sessionId);
        sessionRegistry.removeSession(sessionId);
        
        // 연결 종료된 세션의 분석 작업 정리
        analysisManager.handleSessionDisconnect(sessionId);
    }
} 