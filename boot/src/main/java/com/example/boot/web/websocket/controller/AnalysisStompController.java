package com.example.boot.web.websocket.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.RealTimeAnalysisService;
import com.example.boot.web.controller.InfrastructureStatusController;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisStompController {
    
    private final RealTimeAnalysisService analysisService;
    private final InfrastructureStatusController infraStatus;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @MessageMapping("/analysis.start")
    public void startAnalysis(@Payload AnalysisRequest request) {
        log.debug("Received analysis request: {}", request);
        
        // 인프라 상태 확인
        var status = infraStatus.getStatus();
        if (!status.isValid()) {
            log.warn("Infrastructure not ready, status: {}", status);
            messagingTemplate.convertAndSend("/topic/analysis.error", 
                "Trading infrastructure is not ready. Please try again later.");
            return;
        }
        
        log.info("Starting analysis for {}-{}, style: {}", 
                request.getExchange(), request.getCurrencyPair(), request.getTradingStyle());
                
        analysisService.startAnalysis(request)
            .subscribe(
                response -> {
                    try {
                        // JSON 그대로 로그 출력
                        String jsonResponse = objectMapper.writeValueAsString(response);
                        log.info("RAW JSON RESPONSE: {}", jsonResponse);
                        
                        messagingTemplate.convertAndSend("/topic/analysis", response);
                        log.debug("Analysis response sent to /topic/analysis");
                    } catch (Exception e) {
                        log.error("Error serializing response to JSON: {}", e.getMessage(), e);
                    }
                },
                error -> {
                    log.error("Error in analysis stream: {}", error.getMessage(), error);
                    messagingTemplate.convertAndSend("/topic/analysis.error", error.getMessage());
                },
                () -> log.info("Analysis stream completed for {}-{}", request.getExchange(), request.getCurrencyPair())
            );
        
        // 테스트 메시지 전송
        AnalysisResponse testResponse = AnalysisResponse.builder()
            .exchange(request.getExchange())
            .currencyPair(request.getCurrencyPair())
            .cardId(request.getCardId())
            .message("테스트 메시지 - 분석 시작됨")
            .build();
        
        try {
            // 테스트 메시지도 JSON으로 로깅
            String testJsonResponse = objectMapper.writeValueAsString(testResponse);
            log.info("RAW TEST JSON: {}", testJsonResponse);
        } catch (Exception e) {
            log.error("Error serializing test response to JSON: {}", e.getMessage(), e);
        }
        
        log.debug("Sending test message to /topic/analysis");
        messagingTemplate.convertAndSend("/topic/analysis", testResponse);
    }

    @MessageMapping("/analysis.stop")
    public void stopAnalysis(@Payload AnalysisRequest request) {
        log.info("Stopping analysis for {}-{}", request.getExchange(), request.getCurrencyPair());
        analysisService.stopAnalysis(request);
        messagingTemplate.convertAndSend("/topic/analysis.stop", 
            String.format("Analysis stopped for %s-%s", request.getExchange(), request.getCurrencyPair()));
    }
} 