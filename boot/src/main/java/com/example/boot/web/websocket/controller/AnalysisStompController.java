package com.example.boot.web.websocket.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.RealTimeAnalysisService;
import com.example.boot.web.controller.InfrastructureStatusController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisStompController {
    
    private final RealTimeAnalysisService analysisService;
    private final InfrastructureStatusController infraStatus;

    @MessageMapping("/analysis.start")
    @SendTo("/topic/analysis")
    public Flux<AnalysisResponse> startAnalysis(@Payload AnalysisRequest request) {
        // 인프라 상태 확인
        var status = infraStatus.getStatus();
        if (!status.isValid()) {
            return Flux.error(new IllegalStateException("Trading infrastructure is not ready. Please try again later."));
        }
        
        log.info("Starting analysis for {}-{}, style: {}", 
                request.getExchange(), request.getCurrencyPair(), request.getTradingStyle());
        return analysisService.startAnalysis(request);
    }

    @MessageMapping("/analysis.stop")
    public void stopAnalysis(@Payload AnalysisRequest request) {
        log.info("Stopping analysis for {}-{}", request.getExchange(), request.getCurrencyPair());
        analysisService.stopAnalysis(request);
    }
} 