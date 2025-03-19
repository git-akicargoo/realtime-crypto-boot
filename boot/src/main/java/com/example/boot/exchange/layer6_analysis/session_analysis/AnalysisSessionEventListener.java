package com.example.boot.exchange.layer6_analysis.session_analysis;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 분석 웹소켓 세션 이벤트 리스너
 * 분석과 관련된 세션 연결 및 연결 해제 이벤트를 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisSessionEventListener {
    private final AnalysisSessionRegistry sessionRegistry;
    private final AnalysisManager analysisManager;
    
    /**
     * 세션 연결 이벤트 처리
     */
    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.debug("Analysis session connected: {}", sessionId);
        sessionRegistry.registerSession(sessionId);
    }
    
    /**
     * 세션 연결 해제 이벤트 처리
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("Analysis session disconnected: {}", sessionId);
        
        // 세션 제거
        sessionRegistry.removeSession(sessionId);
        
        // 관련 분석 작업 정리
        analysisManager.handleSessionDisconnect(sessionId);
    }
} 