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
        double smaMediumDiff = calculateSMADifference(history, smaShortPeriod * 2);
        double smaLongDiff = calculateSMADifference(history, smaLongPeriod);
        boolean smaBreakout = isSMABreakout(smaShortDiff, smaLongDiff);
        String smaSignal = calculateSMASignal(smaShortDiff, smaMediumDiff, smaLongDiff);

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

        AnalysisResponse response = AnalysisResponse.builder()
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
            .smaMediumDifference(smaMediumDiff)
            .sma3Difference(smaLongDiff)
            .smaBreakout(smaBreakout)
            .smaSignal(smaSignal)
            .build();
        
        log.debug("Analysis response: {}", response);
        return response;
    }
    
    private String determineAnalysisResult(double reboundProb) {
        if (reboundProb >= 80) return "VERY_STRONG_REBOUND";
        if (reboundProb >= 60) return "STRONG_REBOUND";
        if (reboundProb >= 40) return "POSSIBLE_REBOUND";
        if (reboundProb >= 20) return "WEAK_REBOUND";
        if (reboundProb >= 10) return "VERY_WEAK_REBOUND";
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
            smaResults, rsiResults, bollingerResults, 
            (double) volumeResults.getOrDefault("changePercent", 0.0), 
            (double) volumeResults.getOrDefault("priceChange", 0.0));
        
        // 6. 과매수/과매도 상태 판단
        log.info("분석에 사용되는 값들: rsiSignal={}, rsiValue={}, bollingerSignal={}, smaSignal={}", 
            rsiResults.get("signal"), rsiResults.get("value"), 
            bollingerResults.get("signal"), smaResults.get("signal"));

        Map<String, Object> marketCondition = determineMarketCondition(
            (String) rsiResults.get("signal"), 
            (double) rsiResults.get("value"),
            (String) bollingerResults.get("signal"), 
            (String) smaResults.get("signal")
        );

        log.info("시장 상태 결정 결과: condition={}, strength={}", 
            marketCondition.get("condition"), marketCondition.get("strength"));
        
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
            .volumeChangePercent((double) volumeResults.get("changePercent"))
            .marketCondition((String) marketCondition.get("condition"))
            .marketConditionStrength((double) marketCondition.get("strength"));
            
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
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("SMA 계산: 최소 2개 이상의 데이터가 필요합니다. (데이터 수: {})", history.size());
            return null;
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
            
            // 단순 평균 계산 (데이터가 적을 경우)
            double simpleAvg = history.stream()
                .mapToDouble(d -> d.getPrice().doubleValue())
                .average()
                .orElse(currentPrice);
            
            // 데이터가 충분하지 않을 경우 간소화된 계산
            if (history.size() < shortPeriod) {
                log.info("단기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                shortSMA = simpleAvg;
            } else {
                shortSMA = calculateMovingAverage(history, shortPeriod);
            }
            
            if (history.size() < mediumPeriod) {
                log.info("중기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                // 중기 SMA가 없어도 단기 SMA로 계산 가능
                mediumSMA = shortSMA;
            } else {
                mediumSMA = calculateMovingAverage(history, mediumPeriod);
            }
            
            if (history.size() < longPeriod) {
                log.info("장기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                // 장기 SMA가 없어도 중기 SMA로 계산 가능
                longSMA = mediumSMA;
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
            return null;
        }
        
        return results;
    }
    
    // 새로운 메서드: RSI 지표 계산
    private Map<String, Object> calculateRSIIndicators(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getRsiPeriod();
        int overbought = request.getRsiOverbought();
        int oversold = request.getRsiOversold();
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("RSI 계산: 최소 2개 이상의 데이터가 필요합니다. (현재: {})", history.size());
            return null;
        }
        
        try {
            // RSI 계산을 위한 가격 데이터 추출
            List<Double> prices = history.stream()
                                       .map(data -> data.getPrice().doubleValue())
                                       .collect(Collectors.toList());
            
            // 간소화된 RSI 계산 (데이터가 적을 경우)
            double rsi;
            if (history.size() < period + 1) {
                log.info("RSI 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                rsi = calculateSimplifiedRSI(prices);
            } else {
                // 충분한 데이터가 있으면 정상 RSI 계산
                rsi = calculateRSI(prices, period);
            }
            
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
            return null;
        }
        
        return results;
    }
    
    // 간소화된 RSI 계산 (데이터가 적을 경우)
    private double calculateSimplifiedRSI(List<Double> prices) {
        if (prices.size() < 2) return 50.0;
        
        // 가격 변화 계산
        double totalGain = 0;
        double totalLoss = 0;
        int count = 0;
        
        for (int i = 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                totalGain += change;
            } else {
                totalLoss += Math.abs(change);
            }
            count++;
        }
        
        // 평균 이득/손실 계산
        double avgGain = totalGain / count;
        double avgLoss = totalLoss / count;
        
        // RSI 계산
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
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
    
    // 볼린저 밴드 계산 메서드 수정 - 최소 데이터로도 작동하도록
    private Map<String, Object> calculateBollingerBands(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getBollingerPeriod();
        double deviation = request.getBollingerDeviation();
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("볼린저 밴드 계산: 최소 2개 이상의 데이터가 필요합니다. (현재: {})", history.size());
            return null;
        }
        
        try {
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // 간소화된 볼린저 밴드 계산 (데이터가 적을 경우)
            double middleBand, upperBand, lowerBand, bandWidth;
            
            if (history.size() < period) {
                log.info("볼린저 밴드 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                
                // 단순 이동평균 계산
                double sum = 0;
                for (StandardExchangeData data : history) {
                    sum += data.getPrice().doubleValue();
                }
                middleBand = sum / history.size();
                
                // 단순 표준편차 계산
                double sumSquaredDiff = 0;
                for (StandardExchangeData data : history) {
                    double diff = data.getPrice().doubleValue() - middleBand;
                    sumSquaredDiff += diff * diff;
                }
                double stdDev = Math.sqrt(sumSquaredDiff / history.size());
                
                // 밴드 계산
                upperBand = middleBand + (stdDev * deviation);
                lowerBand = middleBand - (stdDev * deviation);
                bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            } else {
                // 충분한 데이터가 있으면 정상 볼린저 밴드 계산
                middleBand = calculateMovingAverage(history, period * 60);
                
                // 표준 편차 계산
                double sum = 0;
                for (int i = Math.max(0, history.size() - period); i < history.size(); i++) {
                    double price = history.get(i).getPrice().doubleValue();
                    sum += Math.pow(price - middleBand, 2);
                }
                double stdDev = Math.sqrt(sum / Math.min(period, history.size()));
                
                // 상단 및 하단 밴드
                upperBand = middleBand + (stdDev * deviation);
                lowerBand = middleBand - (stdDev * deviation);
                
                // 밴드 폭 (변동성 지표)
                bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            }
            
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
            return null;
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
    
    // 매수 신호 강도 계산 (새로운 버전)
    private double calculateBuySignalStrength(
            Map<String, Object> smaResults, 
            Map<String, Object> rsiResults, 
            Map<String, Object> bbResults,
            double priceChange,
            double volumeChange) {
        
        // 모든 지표가 null이면 계산 불가
        if (smaResults == null && rsiResults == null && bbResults == null) {
            return 0.0;
        }
        
        double smaSignalStrength = 0.0;
        double rsiSignalStrength = 0.0;
        double bbSignalStrength = 0.0;
        
        // SMA 신호 강도
        if (smaResults != null && smaResults.containsKey("signalStrength")) {
            smaSignalStrength = (Double) smaResults.get("signalStrength");
        }
        
        // RSI 신호 강도
        if (rsiResults != null && rsiResults.containsKey("signalStrength")) {
            rsiSignalStrength = (Double) rsiResults.get("signalStrength");
        }
        
        // 볼린저 밴드 신호 강도
        if (bbResults != null && bbResults.containsKey("signalStrength")) {
            bbSignalStrength = (Double) bbResults.get("signalStrength");
        }
        
        // 가중치 적용 (SMA: 40%, RSI: 30%, BB: 30%)
        double weightedStrength = (smaSignalStrength * 0.4) + 
                                 (rsiSignalStrength * 0.3) + 
                                 (bbSignalStrength * 0.3);
        
        // 가격 및 거래량 변화에 따른 보정
        if (priceChange < -5 && volumeChange > 50) {
            // 급격한 가격 하락과 거래량 증가는 반등 가능성 증가
            weightedStrength += 10;
        } else if (priceChange > 5 && volumeChange > 50) {
            // 급격한 가격 상승과 거래량 증가는 추세 지속 가능성 증가
            weightedStrength += 5;
        }
        
        // 최종 신호 강도 (0-100% 범위 내로 조정)
        return Math.max(0, Math.min(100, weightedStrength));
    }
    
    // 새로운 메서드: 과매수/과매도 상태 판단
    private Map<String, Object> determineMarketCondition(
            String rsiSignal, double rsiValue,
            String bollingerSignal, String smaSignal) {
        
        Map<String, Object> result = new HashMap<>();
        String condition = "NEUTRAL";
        double strength = 0.0;
        
        // RSI 기반 판단 (기본 로직)
        if ("OVERBOUGHT".equals(rsiSignal)) {
            condition = "OVERBOUGHT";
            strength = Math.min(100, (rsiValue - 70) * 3.33); // 70-100 범위를 0-100%로 변환
        } else if ("OVERSOLD".equals(rsiSignal)) {
            condition = "OVERSOLD";
            strength = Math.min(100, (30 - rsiValue) * 3.33); // 0-30 범위를 0-100%로 변환
        } else {
            // 중립 상태에서도 RSI 값에 따라 약한 과매수/과매도 경향 판단
            if (rsiValue > 55) { // 중립이지만 과매수 쪽으로 기울어짐
                condition = "OVERBOUGHT";
                strength = (rsiValue - 55) * 6.67; // 55-70 범위를 0-100%로 매핑
            } else if (rsiValue < 45) { // 중립이지만 과매도 쪽으로 기울어짐
                condition = "OVERSOLD";
                strength = (45 - rsiValue) * 6.67; // 30-45 범위를 0-100%로 매핑
            }
        }
        
        // 볼린저 밴드와 SMA 신호를 고려하여 강도 조정
        if ("UPPER_BAND".equals(bollingerSignal) || "UPPER_HALF".equals(bollingerSignal)) {
            if ("OVERBOUGHT".equals(condition)) {
                strength += 10; // 과매수 신호 강화
            } else if ("NEUTRAL".equals(condition)) {
                condition = "OVERBOUGHT";
                strength = 15;
            }
        } else if ("LOWER_BAND".equals(bollingerSignal) || "LOWER_HALF".equals(bollingerSignal)) {
            if ("OVERSOLD".equals(condition)) {
                strength += 10; // 과매도 신호 강화
            } else if ("NEUTRAL".equals(condition)) {
                condition = "OVERSOLD";
                strength = 15;
            }
        }
        
        // SMA 트렌드 반영
        if ("STRONG_UPTREND".equals(smaSignal) || "UPTREND".equals(smaSignal)) {
            if ("OVERBOUGHT".equals(condition)) {
                strength += 15; // 과매수 상태에서 상승 트렌드면 신호 강화
            } else if ("NEUTRAL".equals(condition)) {
                condition = "OVERBOUGHT";
                strength = 20;
            } else { // OVERSOLD
                strength = Math.max(0, strength - 10); // 과매도 신호 약화
            }
        } else if ("STRONG_DOWNTREND".equals(smaSignal) || "DOWNTREND".equals(smaSignal)) {
            if ("OVERSOLD".equals(condition)) {
                strength += 15; // 과매도 상태에서 하락 트렌드면 신호 강화
            } else if ("NEUTRAL".equals(condition)) {
                condition = "OVERSOLD";
                strength = 20;
            } else { // OVERBOUGHT
                strength = Math.max(0, strength - 10); // 과매수 신호 약화
            }
        }
        
        // 최대값 제한
        strength = Math.min(100, strength);
        
        result.put("condition", condition);
        result.put("strength", strength);
        
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

    // 새로운 메서드 추가: SMA 신호 계산
    private String calculateSMASignal(double smaShortDiff, double smaMediumDiff, double smaLongDiff) {
        if (smaShortDiff > 0 && smaMediumDiff > 0 && smaLongDiff > 0) {
            return "STRONG_UPTREND"; // 강한 상승 추세
        } else if (smaShortDiff > 0 && smaShortDiff > smaMediumDiff) {
            return "BUY"; // 상승 추세
        } else if (smaShortDiff < 0 && smaMediumDiff < 0 && smaLongDiff < 0) {
            return "STRONG_DOWNTREND"; // 강한 하락 추세
        } else if (smaShortDiff < 0 && smaShortDiff < smaMediumDiff) {
            return "SELL"; // 하락 추세
        } else if (smaShortDiff > 0 && smaMediumDiff < 0) {
            return "BULLISH"; // 단기 상승 (매수 신호)
        } else if (smaShortDiff < 0 && smaMediumDiff > 0) {
            return "BEARISH"; // 단기 하락 (매도 신호)
        } else {
            return "NEUTRAL"; // 중립
        }
    }

    // 새로운 메서드: 반등 분석 (AnalysisRequest 사용)
    public Map<String, Object> analyzeRebound(StandardExchangeData currentData, 
                                            List<StandardExchangeData> history,
                                            AnalysisRequest request) {
        log.info("Analyzing rebound for {} - {}", currentData.getExchange(), currentData.getCurrencyPair());
        
        if (history.isEmpty()) {
            log.warn("No historical data available for analysis");
            return null;
        }
        
        Map<String, Object> results = new HashMap<>();
        
        try {
            // 가격 변화율 계산
            double priceChange = calculatePriceChange(currentData, history);
            log.debug("Calculated price change: {}%", priceChange);
            
            // 거래량 변화율 계산
            double volumeChange = calculateVolumeChange(currentData, history);
            log.debug("Calculated volume change: {}%", volumeChange);
            
            // 반등 확률 계산
            double reboundProb = calculateReboundProbability(priceChange, volumeChange);
            log.debug("Calculated rebound probability: {}%", reboundProb);
            
            // 기본 결과 저장
            String reboundResult = determineAnalysisResult(reboundProb);
            results.put("reboundResult", reboundResult); // 반등 결과 저장
            results.put("probability", reboundProb);
            
            // SMA 계산 - 항상 값을 반환하도록 함
            Map<String, Object> smaResults = calculateSMAIndicators(history, request);
            if (smaResults != null) {
                results.put("smaShortDifference", smaResults.get("shortDiff"));
                results.put("smaMediumDifference", smaResults.get("mediumDiff"));
                results.put("smaLongDifference", smaResults.get("longDiff"));
                results.put("smaBreakout", smaResults.get("breakout"));
                results.put("smaSignal", smaResults.get("signal"));
            } else if (history.size() >= 2) {
                // 최소 데이터로 SMA 계산
                double currentPrice = currentData.getPrice().doubleValue();
                double prevPrice = history.get(history.size() - 1).getPrice().doubleValue();
                double diff = ((currentPrice - prevPrice) / prevPrice) * 100;
                
                results.put("smaShortDifference", diff);
                results.put("smaMediumDifference", diff);
                results.put("smaLongDifference", diff);
                results.put("smaBreakout", diff > 0);
                results.put("smaSignal", diff > 0 ? "BULLISH" : "BEARISH");
            }
            
            // RSI 계산 - 항상 값을 반환하도록 함
            Map<String, Object> rsiResults = calculateRSIIndicators(history, request);
            if (rsiResults != null) {
                results.put("rsiValue", rsiResults.get("value"));
                results.put("rsiSignal", rsiResults.get("signal"));
            } else if (history.size() >= 2) {
                // 최소 데이터로 RSI 계산
                double currentPrice = currentData.getPrice().doubleValue();
                double prevPrice = history.get(history.size() - 1).getPrice().doubleValue();
                double change = currentPrice - prevPrice;
                
                // 간단한 RSI 계산 (2개 데이터 기준)
                double rsiValue;
                if (change > 0) {
                    rsiValue = 70.0; // 상승 시 과매수 쪽으로
                } else if (change < 0) {
                    rsiValue = 30.0; // 하락 시 과매도 쪽으로
                } else {
                    rsiValue = 50.0; // 변화 없음
                }
                
                String rsiSignal;
                if (rsiValue >= 70) {
                    rsiSignal = "OVERBOUGHT";
                } else if (rsiValue <= 30) {
                    rsiSignal = "OVERSOLD";
                } else {
                    rsiSignal = "NEUTRAL";
                }
                
                results.put("rsiValue", rsiValue);
                results.put("rsiSignal", rsiSignal);
            }
            
            // 볼린저 밴드 계산 - 항상 값을 반환하도록 함
            Map<String, Object> bbResults = calculateBollingerBands(history, request);
            if (bbResults != null) {
                results.put("bollingerUpper", bbResults.get("upper"));
                results.put("bollingerMiddle", bbResults.get("middle"));
                results.put("bollingerLower", bbResults.get("lower"));
                results.put("bollingerWidth", bbResults.get("width"));
                results.put("bollingerSignal", bbResults.get("signal"));
            } else if (history.size() >= 2) {
                // 최소 데이터로 볼린저 밴드 계산
                double currentPrice = currentData.getPrice().doubleValue();
                double prevPrice = history.get(history.size() - 1).getPrice().doubleValue();
                double avgPrice = (currentPrice + prevPrice) / 2;
                double diff = Math.abs(currentPrice - prevPrice);
                
                // 간단한 볼린저 밴드 계산 (2개 데이터 기준)
                double middle = avgPrice;
                double upper = middle + (diff * 2);
                double lower = middle - (diff * 2);
                double width = ((upper - lower) / middle) * 100;
                
                String signal;
                if (currentPrice >= upper) {
                    signal = "UPPER_TOUCH";
                } else if (currentPrice <= lower) {
                    signal = "LOWER_TOUCH";
                } else if (currentPrice > middle) {
                    signal = "UPPER_HALF";
                } else if (currentPrice < middle) {
                    signal = "LOWER_HALF";
                } else {
                    signal = "MIDDLE_CROSS";
                }
                
                results.put("bollingerUpper", upper);
                results.put("bollingerMiddle", middle);
                results.put("bollingerLower", lower);
                results.put("bollingerWidth", width);
                results.put("bollingerSignal", signal);
            }
            
            // 매수 신호 강도 계산
            double buySignalStrength;
            if (smaResults != null && rsiResults != null && bbResults != null) {
                buySignalStrength = calculateBuySignalStrength(
                    smaResults, rsiResults, bbResults, priceChange, volumeChange);
            } else {
                // 간단한 매수 신호 강도 계산 (기본 지표가 없을 때)
                if (priceChange > 0 && volumeChange > 0) {
                    buySignalStrength = 70.0; // 가격과 거래량 모두 증가 시 강한 매수 신호
                } else if (priceChange > 0) {
                    buySignalStrength = 60.0; // 가격만 증가 시 중간 매수 신호
                } else if (priceChange < 0 && volumeChange > 50) {
                    buySignalStrength = 55.0; // 가격 하락, 거래량 급증 시 약한 매수 신호 (반등 가능성)
                } else if (priceChange < 0) {
                    buySignalStrength = 30.0; // 가격 하락 시 매도 신호
                } else {
                    buySignalStrength = 50.0; // 중립
                }
            }
            results.put("buySignalStrength", buySignalStrength);
            
            // 시장 상태 분석
            Map<String, Object> marketCondition;
            if (smaResults != null && rsiResults != null && bbResults != null) {
                marketCondition = analyzeMarketCondition(
                    smaResults, rsiResults, bbResults, priceChange, volumeChange);
            } else {
                // 간단한 시장 상태 분석 (기본 지표가 없을 때)
                marketCondition = new HashMap<>();
                if (priceChange > 3) {
                    marketCondition.put("condition", "OVERBOUGHT");
                    marketCondition.put("strength", 70.0);
                } else if (priceChange < -3) {
                    marketCondition.put("condition", "OVERSOLD");
                    marketCondition.put("strength", 70.0);
                } else {
                    marketCondition.put("condition", "NEUTRAL");
                    marketCondition.put("strength", 50.0);
                }
            }
            
            if (marketCondition != null) {
                results.put("marketCondition", marketCondition.get("condition"));
                results.put("marketConditionStrength", marketCondition.get("strength"));
            }
            
            // 매수/매도 신호로 변환된 분석 결과 설정
            String tradingSignal = determineTradingSignal(buySignalStrength);
            results.put("result", tradingSignal);
            
            return results;
        } catch (Exception e) {
            log.error("Error in rebound analysis: {}", e.getMessage(), e);
            return null;
        }
    }
    
    // 시장 상태 분석
    private Map<String, Object> analyzeMarketCondition(
            Map<String, Object> smaResults, 
            Map<String, Object> rsiResults, 
            Map<String, Object> bbResults,
            double priceChange,
            double volumeChange) {
        
        // 모든 지표가 null이면 계산 불가
        if (smaResults == null && rsiResults == null && bbResults == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        String condition = "NEUTRAL";
        double strength = 50.0;
        
        // RSI 기반 과매수/과매도 상태 확인
        if (rsiResults != null && rsiResults.containsKey("signal")) {
            String rsiSignal = (String) rsiResults.get("signal");
            if ("OVERBOUGHT".equals(rsiSignal)) {
                condition = "OVERBOUGHT";
                strength = 80.0;
            } else if ("OVERSOLD".equals(rsiSignal)) {
                condition = "OVERSOLD";
                strength = 20.0;
            }
        }
        
        // SMA 기반 추세 확인
        if (smaResults != null && smaResults.containsKey("signal")) {
            String smaSignal = (String) smaResults.get("signal");
            if ("STRONG_UPTREND".equals(smaSignal)) {
                if (!"OVERBOUGHT".equals(condition)) {
                    condition = "BULLISH";
                    strength = 70.0;
                }
            } else if ("STRONG_DOWNTREND".equals(smaSignal)) {
                if (!"OVERSOLD".equals(condition)) {
                    condition = "BEARISH";
                    strength = 30.0;
                }
            }
        }
        
        // 볼린저 밴드 기반 변동성 확인
        if (bbResults != null && bbResults.containsKey("width")) {
            double bbWidth = (Double) bbResults.get("width");
            if (bbWidth > 5.0) {
                // 높은 변동성
                result.put("volatility", "HIGH");
            } else if (bbWidth < 2.0) {
                // 낮은 변동성
                result.put("volatility", "LOW");
            } else {
                // 보통 변동성
                result.put("volatility", "MEDIUM");
            }
        }
        
        result.put("condition", condition);
        result.put("strength", strength);
        return result;
    }
}