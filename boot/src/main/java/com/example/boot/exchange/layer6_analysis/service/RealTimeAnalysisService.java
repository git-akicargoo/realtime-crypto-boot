package com.example.boot.exchange.layer6_analysis.service;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.executor.TradingAnalysisExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeAnalysisService {
    
    private final TradingAnalysisExecutor executor;
    private final RedisCacheService cacheService;
    
    public Flux<AnalysisResponse> startAnalysis(AnalysisRequest request) {
        // 카드 ID 생성 (baseCardId-uuid)
        String baseCardId = (request.getExchange() + "-" + request.getCurrencyPair()).toLowerCase();
        String uuid = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
        String cardId = baseCardId + "-" + uuid;
        long timestamp = System.currentTimeMillis();
        
        // 요청 객체에 카드 ID와 타임스탬프 설정
        request.setCardId(cardId);
        request.setTimestamp(timestamp);
        
        log.info("Starting analysis with cardId: {}", cardId);
        
        // CurrencyPair 객체 생성
        CurrencyPair currencyPair = request.toCurrencyPair();
        if (currencyPair == null) {
            return Flux.error(new IllegalArgumentException("Invalid currency pair"));
        }
        
        var latestData = cacheService.getLatestData(request.getExchange(), currencyPair.toString());
        if (latestData == null) {
            return Flux.error(new IllegalStateException("No data available for analysis"));
        }

        return Flux.just(executor.executeAnalysis(
            latestData,
            request.getPriceDropThreshold(),
            request.getVolumeIncreaseThreshold(),
            request.getSmaShortPeriod(),
            request.getSmaLongPeriod()
        )).map(response -> enrichResponse(response, request));
    }
    
    public void stopAnalysis(AnalysisRequest request) {
        log.info("Stopping analysis for cardId: {}", request.getCardId());
        executor.stopAnalysis(request.getExchange(), request.getCurrencyPair());
    }
    
    private AnalysisResponse enrichResponse(AnalysisResponse response, AnalysisRequest request) {
        return AnalysisResponse.builder()
            .exchange(response.getExchange())
            .currencyPair(response.getCurrencyPair())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(response.getAnalysisTime())
            .currentPrice(response.getCurrentPrice())
            .priceChangePercent(response.getPriceChangePercent())
            .volumeChangePercent(response.getVolumeChangePercent())
            .reboundProbability(response.getReboundProbability())
            .analysisResult(response.getAnalysisResult())
            .message(response.getMessage())
            .tradingStyle(request.getTradingStyle())
            .cardId(request.getCardId())
            .timestamp(request.getTimestamp())
            .sma1Difference(response.getSma1Difference())
            .smaMediumDifference(response.getSmaMediumDifference())
            .sma3Difference(response.getSma3Difference())
            .smaBreakout(response.isSmaBreakout())
            .smaSignal(response.getSmaSignal())
            .build();
    }
} 