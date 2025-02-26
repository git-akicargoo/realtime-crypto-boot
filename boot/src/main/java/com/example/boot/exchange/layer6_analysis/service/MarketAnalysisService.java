package com.example.boot.exchange.layer6_analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAnalysisService {
    private final RedisCacheService cacheService;
    private final Map<String, Flux<AnalysisResponse>> activeAnalyses = new ConcurrentHashMap<>();
    
    public AnalysisResponse analyzeRebound(StandardExchangeData currentData, 
                                         double priceDropThreshold, 
                                         double volumeIncreaseThreshold,
                                         int smaShortPeriod,
                                         int smaLongPeriod) {
        log.info("Starting analysis for {} - {}", currentData.getExchange(), currentData.getCurrencyPair());
        
        List<StandardExchangeData> history = cacheService.getAnalysisWindow(
            currentData.getExchange(), 
            currentData.getCurrencyPair().toString()
        );
        
        log.info("Found historical data: {} entries", history.size());
        
        if (history.isEmpty()) {
            return AnalysisResponse.builder()
                .exchange(currentData.getExchange())
                .currencyPair(currentData.getCurrencyPair().toString())
                .message("No recent data available for analysis")
                .build();
        }

        // 현재 데이터를 history의 마지막 데이터로 사용
        StandardExchangeData latestData = history.get(history.size() - 1);
        
        // 가격 변화율 계산 (현재 데이터와 이전 데이터 비교)
        double priceChange = calculatePriceChange(latestData, history.subList(0, history.size() - 1));
        log.info("Calculated price change: {}%", priceChange);

        // 거래량 변화율 계산
        double volumeChange = calculateVolumeChange(latestData, history.subList(0, history.size() - 1));
        log.info("Calculated volume change: {}%", volumeChange);

        // 반등 확률 계산
        double reboundProb = calculateReboundProbability(priceChange, volumeChange);
        log.info("Calculated rebound probability: {}%", reboundProb);

        // SMA 계산 (동적 기간 적용)
        double smaShortDiff = calculateSMADifference(history, smaShortPeriod);
        double smaLongDiff = calculateSMADifference(history, smaLongPeriod);
        boolean smaBreakout = isSMABreakout(smaShortDiff, smaLongDiff);

        return AnalysisResponse.builder()
            .exchange(latestData.getExchange())
            .currencyPair(latestData.getCurrencyPair().toString())
            .analysisTime(LocalDateTime.now())
            .currentPrice(latestData.getPrice().doubleValue())
            .priceChangePercent(priceChange)
            .volumeChangePercent(volumeChange)
            .reboundProbability(reboundProb)
            .analysisResult(determineAnalysisResult(reboundProb))
            .message(generateAnalysisMessage(priceChange, volumeChange, reboundProb))
            .sma1Difference(smaShortDiff)
            .sma3Difference(smaLongDiff)
            .smaBreakout(smaBreakout)
            .build();
    }
    
    private String determineAnalysisResult(double reboundProb) {
        if (reboundProb >= 70) return "STRONG_REBOUND";
        if (reboundProb >= 40) return "POSSIBLE_REBOUND";
        return "NO_REBOUND";
    }
    
    private String generateAnalysisMessage(double priceChange, double volumeChange, double reboundProb) {
        return String.format(
            "Price change: %.2f%%, Volume change: %.2f%%, Rebound probability: %.2f%%",
            priceChange, volumeChange, reboundProb
        );
    }
    
    private double calculatePriceChange(StandardExchangeData current, List<StandardExchangeData> history) {
        if (history.isEmpty()) return 0.0;
        
        // 시간 순서대로 정렬된 데이터를 시간대별로 분석
        List<StandardExchangeData> sortedHistory = new ArrayList<>(history);
        Collections.sort(sortedHistory, Comparator.comparing(StandardExchangeData::getTimestamp));
        
        // 단기(1분), 중기(5분), 장기(15분) 이동평균 계산
        double shortTermAvg = calculateMovingAverage(sortedHistory, 60);  // 1분
        double mediumTermAvg = calculateMovingAverage(sortedHistory, 300);  // 5분
        double longTermAvg = calculateMovingAverage(sortedHistory, 900);  // 15분
        
        log.info("Price Moving Averages - 1min: {}, 5min: {}, 15min: {}, Current: {}", 
            shortTermAvg, mediumTermAvg, longTermAvg, current.getPrice());
        
        // 단기, 중기, 장기 추세를 종합적으로 분석
        double shortTermChange = calculatePercentageChange(current.getPrice().doubleValue(), shortTermAvg);
        double mediumTermChange = calculatePercentageChange(current.getPrice().doubleValue(), mediumTermAvg);
        double longTermChange = calculatePercentageChange(current.getPrice().doubleValue(), longTermAvg);
        
        // 가중치를 적용한 종합 변화율 계산
        return (shortTermChange * 0.5) + (mediumTermChange * 0.3) + (longTermChange * 0.2);
    }
    
    private double calculateMovingAverage(List<StandardExchangeData> data, int seconds) {
        int dataPoints = Math.min(seconds, data.size());
        if (dataPoints == 0) return 0.0;
        
        return data.subList(data.size() - dataPoints, data.size()).stream()
            .mapToDouble(d -> d.getPrice().doubleValue())
            .average()
            .orElse(0.0);
    }
    
    private double calculatePercentageChange(double current, double reference) {
        if (reference == 0) return 0.0;
        return ((current - reference) / reference) * 100;
    }
    
    private double calculateVolumeChange(StandardExchangeData current, List<StandardExchangeData> history) {
        if (history.isEmpty()) return 0.0;
        
        // 전체 데이터의 거래량 통계 계산
        DoubleSummaryStatistics volumeStats = history.stream()
            .map(data -> data.getVolume().doubleValue())
            .mapToDouble(Double::doubleValue)
            .summaryStatistics();
        
        log.info("Volume Statistics - Min: {}, Max: {}, Avg: {}, Current: {}", 
            volumeStats.getMin(), volumeStats.getMax(), volumeStats.getAverage(), current.getVolume());
        
        // 전체 기간의 평균 거래량과 현재 거래량 비교
        double avgVolume = volumeStats.getAverage();
        
        return current.getVolume().subtract(BigDecimal.valueOf(avgVolume))
            .divide(BigDecimal.valueOf(avgVolume), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }
    
    private double calculateReboundProbability(double priceChange, double volumeChange) {
        double priceWeight = 0.6;
        double volumeWeight = 0.4;
        
        // 가격이 평균보다 낮을 때만 반등 확률 계산
        if (priceChange >= 0) {
            return 0.0;
        }
        
        // 가격이 평균보다 낮고 거래량이 증가할 때 반등 가능성 높음
        double normalizedPrice = Math.min(Math.abs(priceChange) / 5.0, 1.0);
        double normalizedVolume = Math.min(volumeChange / 100.0, 1.0);
        
        return (normalizedPrice * priceWeight + normalizedVolume * volumeWeight) * 100;
    }

    public Flux<AnalysisResponse> startRealtimeAnalysis(String exchange, 
                                                       String currencyPair,
                                                       double priceDropThreshold,
                                                       double volumeIncreaseThreshold,
                                                       int smaShortPeriod,
                                                       int smaLongPeriod) {
        return Flux.interval(Duration.ofSeconds(1))
            .map(tick -> {
                StandardExchangeData latestData = cacheService.getLatestData(exchange, currencyPair);
                return analyzeRebound(latestData, 
                                    priceDropThreshold, 
                                    volumeIncreaseThreshold,
                                    smaShortPeriod,
                                    smaLongPeriod);
            });
    }

    public void stopRealtimeAnalysis(AnalysisRequest request) {
        String analysisKey = request.getExchange() + "_" + request.getCurrencyPair();
        activeAnalyses.remove(analysisKey);
        
        // Redis 캐시 클리어
        String cacheKey = String.format("analysis:%s:%s:timeseries", 
            request.getExchange().toLowerCase(), 
            request.getCurrencyPair());
        cacheService.clearAnalysisData(cacheKey);
        
        log.info("Stopped analysis and cleared cache for {}", analysisKey);
    }

    private double calculateSMADifference(List<StandardExchangeData> data, int minutes) {
        double sma = calculateMovingAverage(data, minutes * 60); // minutes를 초로 변환
        if (data.isEmpty()) return 0.0;
        
        double currentPrice = data.get(data.size() - 1).getPrice().doubleValue();
        return calculatePercentageChange(currentPrice, sma);
    }

    private boolean isSMABreakout(double shortDiff, double longDiff) {
        // 단기 이평선이 장기 이평선을 상향돌파하는 경우
        return shortDiff > 0 && longDiff < 0;
    }
} 