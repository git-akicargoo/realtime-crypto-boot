package com.example.boot.exchange.layer6_analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.config.TradingStyleConfig;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.repository.AnalysisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAnalysisService {
    private final RedisCacheService cacheService;
    private final Map<String, Flux<AnalysisResponse>> activeAnalyses = new ConcurrentHashMap<>();
    private final TradingStyleConfig tradingStyleConfig;
    private final AnalysisRepository analysisRepository;
    
    // 기존 메서드 유지
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

        // CurrencyPair에서 symbol과 quoteCurrency 추출
        String currencyPairStr = latestData.getCurrencyPair().toString();
        String symbol = null;
        String quoteCurrency = null;
        
        if (currencyPairStr.contains("-")) {
            String[] parts = currencyPairStr.split("-");
            if (parts.length == 2) {
                quoteCurrency = parts[0];
                symbol = parts[1];
            }
        }

        return AnalysisResponse.builder()
            .exchange(latestData.getExchange())
            .currencyPair(latestData.getCurrencyPair().toString())
            .symbol(symbol)
            .quoteCurrency(quoteCurrency)
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
    
    // 새로운 메서드: 트레이딩 스타일을 고려한 실시간 분석 시작
    public Flux<AnalysisResponse> startRealtimeAnalysis(AnalysisRequest request) {
        log.info("Starting realtime analysis with trading style: {}", request);
        
        // 트레이딩 스타일에 따른 파라미터 설정 적용
        tradingStyleConfig.applyStyleParameters(request);
        
        String analysisKey = request.getExchange() + "_" + request.getCurrencyPair();
        
        // 이미 실행 중인 분석이 있으면 중지
        if (activeAnalyses.containsKey(analysisKey)) {
            log.info("Analysis already running for {}. Stopping previous analysis.", analysisKey);
            stopRealtimeAnalysis(request);
        }
        
        // 분석 간격 설정 (트레이딩 스타일에 따라 다르게 설정 가능)
        int analysisInterval = 1; // 기본값: 1초
        if ("scalping".equals(request.getTradingStyle())) {
            analysisInterval = 1; // 초단타: 1초
        } else if ("dayTrading".equals(request.getTradingStyle())) {
            analysisInterval = 3; // 단타: 3초
        } else if ("swing".equals(request.getTradingStyle())) {
            analysisInterval = 5; // 스윙: 5초
        }
        
        Flux<AnalysisResponse> analysisFlux = Flux.interval(Duration.ofSeconds(analysisInterval))
            .map(tick -> {
                try {
                    // 데이터 가져오기 - getAnalysisWindow 메서드 사용
                    List<StandardExchangeData> history = cacheService.getAnalysisWindow(
                        request.getExchange(), 
                        request.getCurrencyPair()
                    );
                    
                    if (history.isEmpty()) {
                        log.warn("No historical data available for {}-{}", 
                                request.getExchange(), request.getCurrencyPair());
                        return null;
                    }
                    
                    // 종합 분석 수행
                    return performComprehensiveAnalysis(history, request);
                    
                } catch (Exception e) {
                    log.error("Error during analysis: ", e);
                    return null;
                }
            })
            .filter(Objects::nonNull);
        
        activeAnalyses.put(analysisKey, analysisFlux);
        return analysisFlux;
    }
    
    // 기존 메서드 유지 및 확장
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
    
    // 새로운 메서드: 종합 분석 수행
    private AnalysisResponse performComprehensiveAnalysis(
            List<StandardExchangeData> history, AnalysisRequest request) {
        
        // 데이터 정렬 (시간 오름차순)
        List<StandardExchangeData> sortedHistory = new ArrayList<>(history);
        sortedHistory.sort(Comparator.comparing(StandardExchangeData::getTimestamp));
        
        // 현재 가격 및 거래량
        StandardExchangeData latest = sortedHistory.get(sortedHistory.size() - 1);
        double currentPrice = latest.getPrice().doubleValue();
        
        // tradingStyle이 null인 경우 기본값 설정
        String tradingStyle = request.getTradingStyle();
        if (tradingStyle == null) {
            tradingStyle = "DAY_TRADING";
            log.info("Trading style was null, setting default to DAY_TRADING");
        }
        
        // 1. SMA 계산
        Map<String, Object> smaResults = calculateSMAIndicators(sortedHistory, request);
        
        // 2. RSI 계산
        Map<String, Object> rsiResults = calculateRSIIndicators(sortedHistory, request);
        
        // 3. 볼린저 밴드 계산
        Map<String, Object> bollingerResults = calculateBollingerBands(sortedHistory, request);
        
        // 4. 거래량 분석
        Map<String, Object> volumeResults = analyzeVolume(sortedHistory, request);
        
        // 5. 종합 매수 신호 강도 계산
        double buySignalStrength = calculateBuySignalStrength(
            smaResults, rsiResults, bollingerResults, volumeResults, tradingStyle);
        
        // 6. 과매수/과매도 상태 판단
        Map<String, Object> marketCondition = determineMarketCondition(
            rsiResults, bollingerResults, smaResults);
        
        // 7. 가격 변화율 계산
        double priceChangePercent = calculatePriceChange(latest, sortedHistory.subList(0, sortedHistory.size() - 1));
        
        // CurrencyPair에서 symbol과 quoteCurrency 추출
        String currencyPairStr = request.getCurrencyPair();
        String symbol = null;
        String quoteCurrency = null;
        
        if (currencyPairStr.contains("-")) {
            String[] parts = currencyPairStr.split("-");
            if (parts.length == 2) {
                quoteCurrency = parts[0];
                symbol = parts[1];
            }
        }
        
        // 응답 객체 생성
        AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
            .exchange(request.getExchange())
            .currencyPair(currencyPairStr)
            .symbol(symbol)
            .quoteCurrency(quoteCurrency)
            .analysisTime(LocalDateTime.now())
            .currentPrice(currentPrice)
            .tradingStyle(tradingStyle)
            .buySignalStrength(buySignalStrength)
            .priceChangePercent(priceChangePercent)
            .volumeChangePercent((double) volumeResults.get("changePercent"));
            
        // SMA 관련 필드
        if (smaResults.containsKey("shortDiff")) {
            builder.sma1Difference((double) smaResults.get("shortDiff"));
        }
        if (smaResults.containsKey("mediumDiff")) {
            builder.smaMediumDifference((double) smaResults.get("mediumDiff"));
        }
        if (smaResults.containsKey("longDiff")) {
            builder.sma3Difference((double) smaResults.get("longDiff"));
        }
        if (smaResults.containsKey("breakout")) {
            builder.smaBreakout((boolean) smaResults.get("breakout"));
        }
        if (smaResults.containsKey("signal")) {
            builder.smaSignal((String) smaResults.get("signal"));
        }
        
        // RSI 관련 필드
        if (rsiResults.containsKey("value")) {
            builder.rsiValue((double) rsiResults.get("value"));
        }
        if (rsiResults.containsKey("signal")) {
            builder.rsiSignal((String) rsiResults.get("signal"));
        }
        
        // 볼린저 밴드 관련 필드
        if (bollingerResults.containsKey("upper")) {
            builder.bollingerUpper((double) bollingerResults.get("upper"));
        }
        if (bollingerResults.containsKey("middle")) {
            builder.bollingerMiddle((double) bollingerResults.get("middle"));
        }
        if (bollingerResults.containsKey("lower")) {
            builder.bollingerLower((double) bollingerResults.get("lower"));
        }
        if (bollingerResults.containsKey("signal")) {
            builder.bollingerSignal((String) bollingerResults.get("signal"));
        }
        if (bollingerResults.containsKey("width")) {
            builder.bollingerWidth((double) bollingerResults.get("width"));
        }
        
        // 과매수/과매도 상태
        if (marketCondition.containsKey("condition")) {
            builder.marketCondition((String) marketCondition.get("condition"));
        }
        if (marketCondition.containsKey("strength")) {
            builder.marketConditionStrength((double) marketCondition.get("strength"));
        }
        
        // 분석 결과 메시지
        builder.analysisResult(determineTradingSignal(buySignalStrength))
               .message(generateComprehensiveMessage(buySignalStrength, marketCondition, tradingStyle));
        
        return builder.build();
    }
    
    // 매수 신호 강도에 따른 트레이딩 신호 결정
    private String determineTradingSignal(double buySignalStrength) {
        if (buySignalStrength >= 80) return "STRONG_BUY";
        if (buySignalStrength >= 60) return "BUY";
        if (buySignalStrength >= 40) return "NEUTRAL";
        if (buySignalStrength >= 20) return "SELL";
        return "STRONG_SELL";
    }
    
    // 종합 분석 메시지 생성
    private String generateComprehensiveMessage(
            double buySignalStrength, 
            Map<String, Object> marketCondition,
            String tradingStyle) {
        
        String condition = (String) marketCondition.get("condition");
        double strength = (double) marketCondition.get("strength");
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("트레이딩 스타일: %s, ", formatTradingStyle(tradingStyle)));
        message.append(String.format("매수 신호 강도: %.1f%%, ", buySignalStrength));
        
        if ("OVERBOUGHT".equals(condition)) {
            message.append(String.format("과매수 상태 (강도: %.1f%%)", strength));
        } else if ("OVERSOLD".equals(condition)) {
            message.append(String.format("과매도 상태 (강도: %.1f%%)", strength));
        } else {
            message.append("중립 상태");
        }
        
        return message.toString();
    }
    
    private String formatTradingStyle(String style) {
        if (style == null) {
            return "단타"; // 기본값 설정
        }
        
        switch (style.toUpperCase()) {
            case "SCALPING": return "초단타";
            case "DAY_TRADING": return "단타";
            case "SWING": return "스윙";
            default: return style;
        }
    }
    
    // 기존 메서드 유지
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
    
    // 새로운 메서드: SMA 지표 계산
    private Map<String, Object> calculateSMAIndicators(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        // 충분한 데이터가 없으면 기본값 반환
        if (history.size() < 5) {
            log.warn("SMA 계산을 위한 충분한 데이터가 없습니다. 필요: 5, 실제: {}", history.size());
            results.put("shortDiff", 0.0);
            results.put("mediumDiff", 0.0);
            results.put("longDiff", 0.0);
            results.put("breakout", false);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
            return results;
        }
        
        try {
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // SMA 기간 설정
            int shortPeriod = request.getSmaShortPeriod() * 60; // 분 단위로 변환
            int mediumPeriod = request.getSmaMediumPeriod() * 60;
            int longPeriod = request.getSmaLongPeriod() * 60;
            
            // 단기, 중기, 장기 SMA 계산
            double shortSMA = 0.0;
            double mediumSMA = 0.0;
            double longSMA = 0.0;
            
            // 데이터가 충분하지 않을 경우 가능한 데이터로 계산
            if (history.size() < shortPeriod) {
                shortSMA = history.stream()
                    .mapToDouble(d -> d.getPrice().doubleValue())
                    .average()
                    .orElse(currentPrice);
                log.warn("단기 SMA 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", shortPeriod, history.size());
            } else {
                shortSMA = calculateMovingAverage(history, shortPeriod);
            }
            
            if (history.size() < mediumPeriod) {
                mediumSMA = history.stream()
                    .mapToDouble(d -> d.getPrice().doubleValue())
                    .average()
                    .orElse(currentPrice);
                log.warn("중기 SMA 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", mediumPeriod, history.size());
            } else {
                mediumSMA = calculateMovingAverage(history, mediumPeriod);
            }
            
            if (history.size() < longPeriod) {
                longSMA = history.stream()
                    .mapToDouble(d -> d.getPrice().doubleValue())
                    .average()
                    .orElse(currentPrice);
                log.warn("장기 SMA 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", longPeriod, history.size());
            } else {
                longSMA = calculateMovingAverage(history, longPeriod);
            }
            
            // 현재 가격과 각 SMA의 차이 계산 (%)
            double shortDiff = calculatePercentageChange(currentPrice, shortSMA);
            double mediumDiff = calculatePercentageChange(currentPrice, mediumSMA);
            double longDiff = calculatePercentageChange(currentPrice, longSMA);
            
            // SMA 돌파 여부 확인
            boolean breakout = isSMABreakout(shortDiff, longDiff);
            
            // SMA 신호 결정
            String signal;
            if (shortDiff > 0 && mediumDiff > 0 && longDiff > 0) {
                signal = "STRONG_UPTREND"; // 강한 상승 추세
            } else if (shortDiff > 0 && mediumDiff > 0) {
                signal = "UPTREND"; // 상승 추세
            } else if (shortDiff < 0 && mediumDiff < 0 && longDiff < 0) {
                signal = "STRONG_DOWNTREND"; // 강한 하락 추세
            } else if (shortDiff < 0 && mediumDiff < 0) {
                signal = "DOWNTREND"; // 하락 추세
            } else if (shortDiff > 0 && mediumDiff < 0) {
                signal = "BULLISH"; // 단기 상승 (매수 신호)
            } else if (shortDiff < 0 && mediumDiff > 0) {
                signal = "BEARISH"; // 단기 하락 (매도 신호)
            } else {
                signal = "NEUTRAL"; // 중립
            }
            
            // SMA 매수 신호 강도 계산 (0-100%)
            double signalStrength;
            if ("STRONG_UPTREND".equals(signal)) {
                signalStrength = 80.0 + Math.min(20.0, shortDiff); // 80% ~ 100%
            } else if ("UPTREND".equals(signal)) {
                signalStrength = 70.0 + Math.min(10.0, shortDiff); // 70% ~ 80%
            } else if ("BULLISH".equals(signal)) {
                signalStrength = 60.0 + Math.min(10.0, shortDiff); // 60% ~ 70%
            } else if ("NEUTRAL".equals(signal)) {
                signalStrength = 50.0;
            } else if ("BEARISH".equals(signal)) {
                signalStrength = 40.0 - Math.min(10.0, Math.abs(shortDiff)); // 30% ~ 40%
            } else if ("DOWNTREND".equals(signal)) {
                signalStrength = 30.0 - Math.min(10.0, Math.abs(shortDiff)); // 20% ~ 30%
            } else { // STRONG_DOWNTREND
                signalStrength = 20.0 - Math.min(20.0, Math.abs(shortDiff)); // 0% ~ 20%
            }
            
            // 결과 저장
            results.put("shortDiff", shortDiff);
            results.put("mediumDiff", mediumDiff);
            results.put("longDiff", longDiff);
            results.put("breakout", breakout);
            results.put("signal", signal);
            results.put("signalStrength", signalStrength);
            
            log.debug("SMA 계산 결과: shortDiff={}, mediumDiff={}, longDiff={}, breakout={}, signal={}, signalStrength={}",
                shortDiff, mediumDiff, longDiff, breakout, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("SMA 계산 중 오류 발생: {}", e.getMessage(), e);
            results.put("shortDiff", 0.0);
            results.put("mediumDiff", 0.0);
            results.put("longDiff", 0.0);
            results.put("breakout", false);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    // 새로운 메서드: RSI 지표 계산
    private Map<String, Object> calculateRSIIndicators(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getRsiPeriod();
        int overbought = request.getRsiOverbought();
        int oversold = request.getRsiOversold();
        
        // 충분한 데이터가 없으면 기본값 반환
        if (history.size() < period + 1) {
            log.warn("RSI 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", period + 1, history.size());
            results.put("value", 50.0);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
            return results;
        }
        
        try {
            // RSI 계산을 위한 가격 데이터 추출
            List<Double> prices = history.stream()
                                       .map(data -> data.getPrice().doubleValue())
                                       .collect(Collectors.toList());
            
            // RSI 계산
            double rsi = calculateRSI(prices, period);
            
            // RSI 신호 결정
            String signal;
            if (rsi >= overbought) {
                signal = "OVERBOUGHT";
            } else if (rsi <= oversold) {
                signal = "OVERSOLD";
            } else {
                signal = "NEUTRAL";
            }
            
            // RSI 매수 신호 강도 계산 (0-100%)
            double signalStrength;
            if (rsi <= oversold) {
                // 과매도 상태는 강한 매수 신호
                signalStrength = 90.0 - (rsi - 10) * 1.5; // 90% ~ 70%
            } else if (rsi <= 40) {
                // 40 이하는 중간 매수 신호
                signalStrength = 60.0 + (40 - rsi); // 60% ~ 70%
            } else if (rsi <= 60) {
                // 40-60은 중립 신호
                signalStrength = 50.0;
            } else if (rsi < overbought) {
                // 60-70은 중간 매도 신호
                signalStrength = 40.0 - (rsi - 60); // 40% ~ 30%
            } else {
                // 과매수 상태는 강한 매도 신호
                signalStrength = 10.0 + (100 - rsi) * 0.5; // 10% ~ 25%
            }
            
            // 결과 저장
            results.put("value", rsi);
            results.put("signal", signal);
            results.put("signalStrength", signalStrength);
            
            log.debug("RSI 계산 결과: value={}, signal={}, signalStrength={}", rsi, signal, signalStrength);
            
        } catch (Exception e) {
            log.error("RSI 계산 중 오류 발생: {}", e.getMessage(), e);
            results.put("value", 50.0);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    // RSI 계산 메서드
    private double calculateRSI(List<Double> prices, int period) {
        if (prices.size() <= period) {
            return 50.0; // 충분한 데이터가 없으면 중립값 반환
        }
        
        double avgGain = 0;
        double avgLoss = 0;
        
        // 첫 번째 평균 이득/손실 계산
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        // 나머지 기간에 대한 평균 이득/손실 계산
        for (int i = period + 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }
        
        // RSI 계산
        if (avgLoss == 0) {
            return 100.0;
        }
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    // 볼린저 밴드 계산 메서드 추가
    private Map<String, Object> calculateBollingerBands(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getBollingerPeriod();
        double deviation = request.getBollingerDeviation();
        
        // 충분한 데이터가 없으면 기본값 반환
        if (history.size() < period) {
            log.warn("볼린저 밴드 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", period, history.size());
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            results.put("upper", currentPrice * 1.05);
            results.put("middle", currentPrice);
            results.put("lower", currentPrice * 0.95);
            results.put("width", 10.0);
            results.put("signal", "INSIDE");
            results.put("signalStrength", 50.0);
            return results;
        }
        
        try {
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // 중간 밴드 (SMA)
            double middleBand = 0.0;
            if (period * 60 > history.size()) {
                // 데이터가 충분하지 않으면 전체 데이터로 계산
                middleBand = history.stream()
                    .mapToDouble(d -> d.getPrice().doubleValue())
                    .average()
                    .orElse(currentPrice);
                log.warn("볼린저 밴드 SMA 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", period * 60, history.size());
            } else {
                middleBand = calculateMovingAverage(history, period * 60);
            }
            
            // 표준 편차 계산
            double sum = 0;
            for (int i = Math.max(0, history.size() - period); i < history.size(); i++) {
                double price = history.get(i).getPrice().doubleValue();
                sum += Math.pow(price - middleBand, 2);
            }
            double stdDev = Math.sqrt(sum / Math.min(period, history.size()));
            
            // 상단 및 하단 밴드
            double upperBand = middleBand + (stdDev * deviation);
            double lowerBand = middleBand - (stdDev * deviation);
            
            // 밴드 폭 (변동성 지표)
            double bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            
            // 볼린저 밴드 신호 결정
            String signal;
            if (currentPrice >= upperBand) {
                signal = "UPPER_TOUCH"; // 상단 밴드 터치 (과매수 가능성)
            } else if (currentPrice <= lowerBand) {
                signal = "LOWER_TOUCH"; // 하단 밴드 터치 (과매도 가능성)
            } else if (currentPrice > middleBand) {
                signal = "UPPER_HALF"; // 중간~상단 밴드 사이
            } else if (currentPrice < middleBand) {
                signal = "LOWER_HALF"; // 중간~하단 밴드 사이
            } else {
                signal = "MIDDLE_CROSS"; // 중간 밴드 교차
            }
            
            // 볼린저 밴드 매수 신호 강도 계산 (0-100%)
            double signalStrength;
            if ("LOWER_TOUCH".equals(signal)) {
                // 하단 밴드 터치는 강한 매수 신호
                signalStrength = 80.0 + (bandWidth / 5.0); // 80% ~ 100%
            } else if ("LOWER_HALF".equals(signal)) {
                // 하단 절반은 중간 매수 신호
                double position = (currentPrice - lowerBand) / ((middleBand - lowerBand) / 2);
                signalStrength = 70.0 - (position * 10.0); // 60% ~ 70%
            } else if ("MIDDLE_CROSS".equals(signal)) {
                // 중간 밴드 교차는 중립 신호
                signalStrength = 50.0;
            } else if ("UPPER_HALF".equals(signal)) {
                // 상단 절반은 중간 매도 신호
                double position = (currentPrice - middleBand) / ((upperBand - middleBand) / 2);
                signalStrength = 40.0 - (position * 10.0); // 30% ~ 40%
            } else { // UPPER_TOUCH
                // 상단 밴드 터치는 강한 매도 신호
                signalStrength = 20.0 - (bandWidth / 5.0); // 0% ~ 20%
            }
            
            // 결과 저장
            results.put("upper", upperBand);
            results.put("middle", middleBand);
            results.put("lower", lowerBand);
            results.put("width", bandWidth);
            results.put("signal", signal);
            results.put("signalStrength", signalStrength);
            
            log.debug("볼린저 밴드 계산 결과: upper={}, middle={}, lower={}, width={}, signal={}, signalStrength={}",
                upperBand, middleBand, lowerBand, bandWidth, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("볼린저 밴드 계산 중 오류 발생: {}", e.getMessage(), e);
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            results.put("upper", currentPrice * 1.05);
            results.put("middle", currentPrice);
            results.put("lower", currentPrice * 0.95);
            results.put("width", 10.0);
            results.put("signal", "INSIDE");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    // 거래량 분석 메서드 추가
    private Map<String, Object> analyzeVolume(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        // 충분한 데이터가 없으면 기본값 반환
        if (history.size() < 10) {
            results.put("changePercent", 0.0);
            results.put("signalStrength", 50.0);
            return results;
        }
        
        // 현재 거래량
        double currentVolume = history.get(history.size() - 1).getVolume().doubleValue();
        
        // 이전 거래량 평균 (최근 10개 데이터)
        double avgVolume = history.subList(history.size() - 10, history.size() - 1).stream()
            .mapToDouble(data -> data.getVolume().doubleValue())
            .average()
            .orElse(0.0);
        
        // 거래량 변화율 계산
        double volumeChangePercent = 0.0;
        if (avgVolume > 0) {
            volumeChangePercent = ((currentVolume - avgVolume) / avgVolume) * 100;
        }
        
        // 거래량 신호 강도 계산 (0-100%)
        double signalStrength;
        if (volumeChangePercent >= 100) {
            // 거래량이 100% 이상 증가하면 매우 강한 신호
            signalStrength = 90.0;
        } else if (volumeChangePercent >= 50) {
            // 거래량이 50-100% 증가하면 강한 신호
            signalStrength = 80.0;
        } else if (volumeChangePercent >= 20) {
            // 거래량이 20-50% 증가하면 중간 신호
            signalStrength = 70.0;
        } else if (volumeChangePercent >= 0) {
            // 거래량이 0-20% 증가하면 약한 신호
            signalStrength = 60.0;
        } else if (volumeChangePercent > -20) {
            // 거래량이 0-20% 감소하면 약한 부정 신호
            signalStrength = 50.0 + volumeChangePercent;
        } else {
            // 거래량이 20% 이상 감소하면 강한 부정 신호
            signalStrength = 30.0;
        }
        
        // 결과 저장
        results.put("changePercent", volumeChangePercent);
        results.put("signalStrength", signalStrength);
        
        return results;
    }
    
    // 새로운 메서드: 종합 매수 신호 강도 계산
    private double calculateBuySignalStrength(
            Map<String, Object> smaResults, 
            Map<String, Object> rsiResults, 
            Map<String, Object> bollingerResults, 
            Map<String, Object> volumeResults, 
            String tradingStyle) {
        
        try {
            // tradingStyle이 null인 경우 기본값 설정
            if (tradingStyle == null) {
                tradingStyle = "DAY_TRADING";
                log.warn("tradingStyle이 null입니다. 기본값 'DAY_TRADING'으로 설정합니다.");
            }
            
            // 트레이딩 스타일에 따른 가중치 가져오기
            Map<String, Double> weights = tradingStyleConfig.getWeightsForStyle(tradingStyle);
            
            // 각 지표별 신호 강도 가져오기
            double smaSignalStrength = (double) smaResults.getOrDefault("signalStrength", 50.0);
            double rsiSignalStrength = (double) rsiResults.getOrDefault("signalStrength", 50.0);
            double bollingerSignalStrength = (double) bollingerResults.getOrDefault("signalStrength", 50.0);
            double volumeSignalStrength = (double) volumeResults.getOrDefault("signalStrength", 50.0);
            
            // 가중 평균 계산
            double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (totalWeight == 0) {
                totalWeight = 4.0; // 기본 가중치 합계
                log.warn("가중치 합계가 0입니다. 기본값 4.0으로 설정합니다.");
            }
            
            double weightedSum = 
                (smaSignalStrength * weights.getOrDefault("sma", 1.0)) +
                (rsiSignalStrength * weights.getOrDefault("rsi", 1.0)) +
                (bollingerSignalStrength * weights.getOrDefault("bollinger", 1.0)) +
                (volumeSignalStrength * weights.getOrDefault("volume", 1.0));
            
            double result = weightedSum / totalWeight;
            
            log.debug("매수 신호 강도 계산 결과: {}, sma={}, rsi={}, bollinger={}, volume={}, tradingStyle={}",
                result, smaSignalStrength, rsiSignalStrength, bollingerSignalStrength, volumeSignalStrength, tradingStyle);
                
            return result;
            
        } catch (Exception e) {
            log.error("매수 신호 강도 계산 중 오류 발생: {}", e.getMessage(), e);
            return 50.0; // 오류 발생 시 중립값 반환
        }
    }
    
    // 새로운 메서드: 과매수/과매도 상태 판단
    private Map<String, Object> determineMarketCondition(
            Map<String, Object> rsiResults,
            Map<String, Object> bollingerResults,
            Map<String, Object> smaResults) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // RSI 신호
            String rsiSignal = (String) rsiResults.getOrDefault("signal", "NEUTRAL");
            double rsiValue = (double) rsiResults.getOrDefault("value", 50.0);
            
            // 볼린저 밴드 신호
            String bollingerSignal = (String) bollingerResults.getOrDefault("signal", "INSIDE");
            
            // SMA 신호
            String smaSignal = (String) smaResults.getOrDefault("signal", "NEUTRAL");
            
            // 과매수/과매도 상태 판단
            String condition = "NEUTRAL";
            double strength = 0.0;
            
            // RSI 기반 과매수/과매도 판단
            if ("OVERBOUGHT".equals(rsiSignal)) {
                condition = "OVERBOUGHT";
                strength = (rsiValue - 50) * 2; // 50-100 범위를 0-100% 강도로 변환
            } else if ("OVERSOLD".equals(rsiSignal)) {
                condition = "OVERSOLD";
                strength = (50 - rsiValue) * 2; // 0-50 범위를 0-100% 강도로 변환
            }
            
            // 볼린저 밴드 신호로 보정
            if ("UPPER_TOUCH".equals(bollingerSignal) && "OVERBOUGHT".equals(condition)) {
                strength += 10; // 상단 돌파 시 과매수 강도 증가
            } else if ("LOWER_TOUCH".equals(bollingerSignal) && "OVERSOLD".equals(condition)) {
                strength += 10; // 하단 돌파 시 과매도 강도 증가
            }
            
            // SMA 신호로 보정
            if ("STRONG_UPTREND".equals(smaSignal) && "OVERBOUGHT".equals(condition)) {
                strength += 5; // 강한 상승 추세 시 과매수 강도 증가
            } else if ("STRONG_DOWNTREND".equals(smaSignal) && "OVERSOLD".equals(condition)) {
                strength += 5; // 강한 하락 추세 시 과매도 강도 증가
            }
            
            // 강도 제한 (0-100%)
            strength = Math.min(100, Math.max(0, strength));
            
            // 결과 저장
            result.put("condition", condition);
            result.put("strength", strength);
            
            log.debug("시장 상태 판단 결과: condition={}, strength={}, rsiSignal={}, rsiValue={}, bollingerSignal={}, smaSignal={}",
                condition, strength, rsiSignal, rsiValue, bollingerSignal, smaSignal);
                
        } catch (Exception e) {
            log.error("시장 상태 판단 중 오류 발생: {}", e.getMessage(), e);
            result.put("condition", "NEUTRAL");
            result.put("strength", 50.0);
        }
        
        return result;
    }

    // startRealtimeAnalysis 메서드 오버로드 추가
    public Flux<AnalysisResponse> startRealtimeAnalysis(
            String exchange, 
            String currencyPair, 
            double priceDropThreshold, 
            double volumeIncreaseThreshold, 
            int smaShortPeriod, 
            int smaLongPeriod) {
        
        AnalysisRequest request = new AnalysisRequest();
        request.setExchange(exchange);
        request.setCurrencyPair(currencyPair);
        request.setPriceDropThreshold(priceDropThreshold);
        request.setVolumeIncreaseThreshold(volumeIncreaseThreshold);
        request.setSmaShortPeriod(smaShortPeriod);
        request.setSmaLongPeriod(smaLongPeriod);
        
        return startRealtimeAnalysis(request);
    }
}