package com.example.boot.exchange.layer6_analysis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.executor.TradingAnalysisExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {
    private final TradingAnalysisExecutor executor;
    private final RedisCacheService cacheService;
    
    @PostMapping("/execute")
    public ResponseEntity<AnalysisResponse> executeAnalysis(@RequestBody AnalysisRequest request) {
        log.info("=== Analysis Request Start ===");
        log.info("Raw request object: {}", request);
        log.info("Exchange: {}", request.getExchange());
        log.info("Pair: {}", request.getCurrencyPair());
        log.info("Price Drop: {}", request.getPriceDropThreshold());
        log.info("Volume Increase: {}", request.getVolumeIncreaseThreshold());
        log.info("=== Analysis Request End ===");

        try {
            log.info("Received analysis request - Exchange: {}, Pair: {}, Drop: {}, Volume: {}", 
                request.getExchange(), request.getCurrencyPair(), 
                request.getPriceDropThreshold(), request.getVolumeIncreaseThreshold());
            
            var latestData = cacheService.getAnalysisWindow(
                request.getExchange(), 
                request.getCurrencyPair()
            );
            
            log.info("Analysis window result - Found: {}, Size: {}", 
                !latestData.isEmpty(), latestData.size());
            
            if (latestData.isEmpty()) {
                return ResponseEntity.ok(
                    AnalysisResponse.builder()
                        .exchange(request.getExchange())
                        .currencyPair(request.getCurrencyPair())
                        .message("No recent data available for analysis")
                        .build()
                );
            }
            
            // 분석 실행 및 결과 반환
            var result = executor.executeAnalysis(
                latestData.get(0),
                request.getPriceDropThreshold(),
                request.getVolumeIncreaseThreshold()
            );
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to execute analysis: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                AnalysisResponse.builder()
                    .exchange(request.getExchange())
                    .currencyPair(request.getCurrencyPair())
                    .message("Analysis failed: " + e.getMessage())
                    .build()
            );
        }
    }
} 