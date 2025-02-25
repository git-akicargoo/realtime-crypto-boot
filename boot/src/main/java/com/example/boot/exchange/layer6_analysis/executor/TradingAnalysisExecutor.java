package com.example.boot.exchange.layer6_analysis.executor;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingAnalysisExecutor {
    private final MarketAnalysisService analysisService;
    
    public AnalysisResponse executeAnalysis(StandardExchangeData data, double priceDropThreshold, double volumeIncreaseThreshold) {
        try {
            return analysisService.analyzeRebound(data, priceDropThreshold, volumeIncreaseThreshold);
        } catch (Exception e) {
            log.error("Analysis execution failed: {}", e.getMessage());
            throw new RuntimeException("Analysis execution failed", e);
        }
    }
} 