package com.example.boot.exchange.layer6_analysis.session_analysis;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 분석 세션 레지스트리
 * 분석과 관련된 활성 STOMP 세션들을 추적하고 관리합니다.
 */
@Slf4j
@Component
public class AnalysisSessionRegistry {
    private final ConcurrentHashMap<String, Object> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * 세션 등록
     */
    public void registerSession(String sessionId) {
        activeSessions.put(sessionId, new Object());
        log.info("Analysis session registered: {}", sessionId);
    }
    
    /**
     * 세션 제거
     */
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        log.info("Analysis session removed: {}", sessionId);
    }
    
    /**
     * 세션 유효성 확인
     */
    public boolean isSessionActive(String sessionId) {
        return sessionId != null && activeSessions.containsKey(sessionId);
    }
    
    /**
     * 활성 세션 수 반환
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
} 