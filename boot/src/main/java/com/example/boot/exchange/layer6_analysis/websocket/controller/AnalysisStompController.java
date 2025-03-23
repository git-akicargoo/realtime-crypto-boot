package com.example.boot.exchange.layer6_analysis.websocket.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.websocket.handler.AnalysisStompHandler;
import com.example.boot.web.controller.InfrastructureStatusController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 분석 STOMP 컨트롤러
 * WebSocket을 통한 실시간 분석 요청을 처리합니다.
 * 컨트롤러 역할에 충실하게 요청을 받아 핸들러로 위임합니다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisStompController {

    private final AnalysisStompHandler analysisHandler;
    private final InfrastructureStatusController infraStatus;
    
    /**
     * 분석 시작 요청 처리
     *
     * @param request 분석 요청 객체
     * @param headerAccessor 메시지 헤더 접근자
     */
    @MessageMapping("/analysis.start")
    public void startAnalysis(@Payload AnalysisRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Received analysis start request via WebSocket: {}, sessionId: {}", request, sessionId);
        
        try {
            // 인프라 상태 확인
            var status = infraStatus.getStatus();
            if (!status.isValid()) {
                log.warn("Infrastructure not ready, status: {}", status);
                analysisHandler.sendErrorMessage(request.getCardId(), "Trading infrastructure is not ready. Please try again later.");
                return;
            }
            
            // 핸들러에 분석 시작 위임
            analysisHandler.startAnalysis(request, sessionId);
        } catch (Exception e) {
            log.error("Error processing analysis start request: {}", e.getMessage(), e);
            analysisHandler.sendErrorMessage(request.getCardId(), e.getMessage());
        }
    }
    
    /**
     * 분석 중지 요청 처리
     *
     * @param request 분석 요청 객체
     * @param headerAccessor 메시지 헤더 접근자
     */
    @MessageMapping("/analysis.stop")
    public void stopAnalysis(@Payload AnalysisRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Received analysis stop request via WebSocket: {}, sessionId: {}", request, sessionId);
        
        try {
            // 핸들러에 분석 중지 위임
            analysisHandler.stopAnalysis(request.getCardId(), request.getExchange(), request.getCurrencyPair());
        } catch (Exception e) {
            log.error("Error processing analysis stop request: {}", e.getMessage(), e);
            analysisHandler.sendErrorMessage(request.getCardId(), e.getMessage());
        }
    }
} 