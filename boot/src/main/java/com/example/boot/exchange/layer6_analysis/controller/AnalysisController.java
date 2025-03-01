package com.example.boot.exchange.layer6_analysis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.executor.TradingAnalysisExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {
    private final TradingAnalysisExecutor executor;
    private final RedisCacheService cacheService;
    
    @PostMapping("/rebound")
    public ResponseEntity<AnalysisResponse> analyzeRebound(@RequestBody AnalysisRequest request) {
        try {
            // CurrencyPair 객체 생성
            CurrencyPair currencyPair = request.toCurrencyPair();
            if (currencyPair == null) {
                return ResponseEntity.badRequest().build();
            }
            
            var data = cacheService.getLatestData(
                request.getExchange(),
                currencyPair.toString()
            );

            if (data == null) {
                return ResponseEntity.notFound().build();
            }

            var result = executor.executeAnalysis(
                data,
                request.getPriceDropThreshold(),
                request.getVolumeIncreaseThreshold(),
                request.getSmaShortPeriod(),
                request.getSmaLongPeriod()
            );
            
            // 응답에 symbol과 quoteCurrency 추가
            if (request.getSymbol() != null && request.getQuoteCurrency() != null) {
                result = AnalysisResponse.builder()
                    .exchange(result.getExchange())
                    .currencyPair(result.getCurrencyPair())
                    .symbol(request.getSymbol())
                    .quoteCurrency(request.getQuoteCurrency())
                    .analysisTime(result.getAnalysisTime())
                    .currentPrice(result.getCurrentPrice())
                    .priceChangePercent(result.getPriceChangePercent())
                    .volumeChangePercent(result.getVolumeChangePercent())
                    .reboundProbability(result.getReboundProbability())
                    .analysisResult(result.getAnalysisResult())
                    .message(result.getMessage())
                    .sma1Difference(result.getSma1Difference())
                    .sma3Difference(result.getSma3Difference())
                    .smaBreakout(result.isSmaBreakout())
                    .build();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Analysis failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopAnalysis(@RequestBody AnalysisRequest request) {
        try {
            // CurrencyPair 객체 생성
            CurrencyPair currencyPair = request.toCurrencyPair();
            if (currencyPair != null) {
                request.setCurrencyPair(currencyPair.toString());
            }
            
            // 분석 작업 중지 및 리소스 정리
            executor.stopAnalysis(request.getExchange(), request.getCurrencyPair());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to stop analysis", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 