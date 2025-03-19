package com.example.boot.exchange.layer6_analysis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.config.TradingStyleConfig;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 암호화폐 시장 분석을 위한 통합 서비스
 * 이전의 MarketAnalysisService, TradingAnalysisExecutor, RealTimeAnalysisService를 통합
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoAnalysisService {
    private final RedisCacheService cacheService;
    private final IndicatorCalculationService indicatorService;
    private final AnalysisResponseConverter responseConverter;
    private final TradingStyleConfig tradingStyleConfig;
    
    // 활성 분석 구독 관리
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    
    // 분석 데이터 캐싱
    private final Map<String, Double> lastPriceChangeMap = new ConcurrentHashMap<>();
    private final Map<String, Double> lastVolumeChangeMap = new ConcurrentHashMap<>();
    private final Map<String, Double> lastReboundProbabilityMap = new ConcurrentHashMap<>();
    
    /**
     * 실시간 분석 시작
     * @param request 분석 요청 객체
     * @return 분석 결과 Flux
     */
    public Flux<AnalysisResponse> startAnalysis(AnalysisRequest request) {
        // 요청 파라미터 검증
        if (request.getExchange() == null || request.getCurrencyPair() == null) {
            log.error("Invalid analysis request: missing exchange or currencyPair");
            return Flux.error(new IllegalArgumentException("Exchange and currency pair must be specified"));
        }
        
        // 트레이딩 스타일에 맞는 파라미터 적용
        tradingStyleConfig.applyStyleParameters(request);
        
        // 카드 ID 생성 및 설정
        String baseCardId = (request.getExchange() + "-" + request.getCurrencyPair()).toLowerCase();
        String uuid = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);
        String cardId = baseCardId + "-" + uuid;
        request.setCardId(cardId);
        request.setTimestamp(System.currentTimeMillis());
        
        log.info("Starting analysis for {} - {} with style {}", 
                request.getExchange(), request.getCurrencyPair(), request.getTradingStyle());
        
        // 구독 키 생성
        String subscriptionKey = getSubscriptionKey(request.getExchange(), request.getCurrencyPair());
        
        // 이미 실행 중인 구독이 있으면 중지
        stopSubscription(subscriptionKey);
        
        // CurrencyPair 객체 생성
        CurrencyPair currencyPair = request.toCurrencyPair();
        if (currencyPair == null) {
            log.error("Invalid currency pair format in request: {}", request);
            return Flux.error(new IllegalArgumentException("Invalid currency pair format"));
        }
        
        // 초기 분석 결과 생성
        AnalysisResponse initialResponse = AnalysisResponse.builder()
            .exchange(request.getExchange())
            .currencyPair(request.getCurrencyPair())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(LocalDateTime.now())
            .currentPrice(0.0)
            .priceChangePercent(0.0)
            .volumeChangePercent(0.0)
            .reboundProbability(0.0)
            .analysisResult("WAITING_FOR_DATA")
            .message("분석 데이터 수집 중...")
            .cardId(cardId)
            .timestamp(request.getTimestamp())
            .tradingStyle(request.getTradingStyle())
            .build();
        
        // 분석 간격 설정 (트레이딩 스타일에 따라 다르게 설정)
        int analysisInterval = getAnalysisInterval(request.getTradingStyle());
        
        // 데이터 구독 및 주기적 분석 설정
        Flux<AnalysisResponse> analysisFlux = Flux.concat(
            Flux.just(initialResponse), // 초기 응답 전송
            
            cacheService.subscribeToMarketData(request.getExchange(), currencyPair.toString())
                .buffer(Duration.ofSeconds(analysisInterval)) // 분석 주기로 데이터 버퍼링
                .flatMap(dataList -> {
                    if (dataList.isEmpty()) {
                        return Mono.empty();
                    }
                    
                    // 버퍼링된 데이터 중 가장 최신 데이터 사용
                    StandardExchangeData latestData = dataList.get(dataList.size() - 1);
                    
                    // 분석용 히스토리 데이터 가져오기
                    return Mono.fromCallable(() -> {
                        List<StandardExchangeData> history = cacheService.getAnalysisWindow(
                            latestData.getExchange(), 
                            latestData.getCurrencyPair().toString()
                        );
                        
                        // 분석 수행
                        AnalysisResponse response = analyzeMarketData(latestData, history, request);
                        
                        // 카드 ID 및 시간 정보 설정
                        response = enrichResponseWithMetadata(response, cardId);
                        
                        return response;
                    });
                })
                .doOnError(e -> log.error("Error during analysis: {}", e.getMessage(), e))
                .doOnComplete(() -> log.info("Analysis complete for {}-{}", request.getExchange(), request.getCurrencyPair()))
        );
        
        // 구독 저장 및 반환
        Disposable subscription = analysisFlux.subscribe();
        activeSubscriptions.put(subscriptionKey, subscription);
        
        return analysisFlux;
    }
    
    /**
     * 분석 중지
     * @param request 분석 요청 객체
     */
    public void stopAnalysis(AnalysisRequest request) {
        String subscriptionKey = getSubscriptionKey(request.getExchange(), request.getCurrencyPair());
        stopSubscription(subscriptionKey);
        
        // 캐시된 데이터 정리
        clearCachedData(subscriptionKey);
        
        // Redis 구독 해제
        cacheService.unsubscribeFromMarketData(request.getExchange(), request.getCurrencyPair());
        
        log.info("Stopped analysis for {}-{}", request.getExchange(), request.getCurrencyPair());
    }
    
    /**
     * 시장 데이터 분석 수행
     * @param data 최신 시장 데이터
     * @param history 히스토리 데이터
     * @param request 분석 요청
     * @return 분석 결과
     */
    public AnalysisResponse analyzeMarketData(StandardExchangeData data, List<StandardExchangeData> history, AnalysisRequest request) {
        if (history.isEmpty()) {
            log.warn("No historical data available for {}-{}", data.getExchange(), data.getCurrencyPair());
            return createInsufficientDataResponse(data, request);
        }
        
        try {
            // 가격 변화율 계산
            String cacheKey = getSubscriptionKey(data.getExchange(), data.getCurrencyPair().toString());
            double priceChangePercent = calculatePriceChange(data, history, cacheKey);
            double volumeChangePercent = calculateVolumeChange(data, history, cacheKey);
            
            // 지표 계산
            Map<String, Object> indicatorResults = calculateIndicators(data, history, request);
            
            // 반등 확률 계산
            double reboundProbability = calculateReboundProbability(priceChangePercent, volumeChangePercent, cacheKey);
            
            // 매수 신호 강도 계산
            double buySignalStrength = calculateBuySignalStrength(indicatorResults, priceChangePercent, volumeChangePercent);
            
            // 시장 상태 판단
            Map<String, Object> marketCondition = determineMarketCondition(indicatorResults);
            
            // 분석 결과를 AnalysisResponse로 변환
            return responseConverter.convertToAnalysisResponse(
                data, request, indicatorResults, priceChangePercent, 
                volumeChangePercent, reboundProbability, buySignalStrength, marketCondition
            );
        } catch (Exception e) {
            log.error("Error analyzing market data: {}", e.getMessage(), e);
            return createErrorResponse(data, request, e);
        }
    }
    
    /**
     * 오류 발생 시 응답 생성
     */
    private AnalysisResponse createErrorResponse(StandardExchangeData data, AnalysisRequest request, Exception e) {
        return AnalysisResponse.builder()
            .exchange(data.getExchange())
            .currencyPair(data.getCurrencyPair().toString())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(LocalDateTime.now())
            .currentPrice(data.getPrice().doubleValue())
            .analysisResult("ERROR")
            .message("분석 중 오류 발생: " + e.getMessage())
            .tradingStyle(request.getTradingStyle())
            .build();
    }
    
    /**
     * 데이터 부족 시 응답 생성
     */
    private AnalysisResponse createInsufficientDataResponse(StandardExchangeData data, AnalysisRequest request) {
        return AnalysisResponse.builder()
            .exchange(data.getExchange())
            .currencyPair(data.getCurrencyPair().toString())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(LocalDateTime.now())
            .currentPrice(data.getPrice().doubleValue())
            .analysisResult("INSUFFICIENT_DATA")
            .message("분석에 필요한 충분한 데이터가 없습니다.")
            .tradingStyle(request.getTradingStyle())
            .build();
    }
    
    /**
     * 가격 변화율 계산
     */
    private double calculatePriceChange(StandardExchangeData data, List<StandardExchangeData> history, String cacheKey) {
        if (history.size() < 2) {
            return lastPriceChangeMap.getOrDefault(cacheKey, 0.0);
        }

        // 직전 데이터와 현재 데이터 비교
        double previousPrice = history.get(history.size() - 2).getPrice().doubleValue();
        double currentPrice = data.getPrice().doubleValue();
        
        // 가격 변화가 있는 경우에만 새로 계산
        if (Math.abs(currentPrice - previousPrice) > 0.000001) {
            double priceChangePercent = ((currentPrice - previousPrice) / previousPrice) * 100;
            // 새 값 저장
            lastPriceChangeMap.put(cacheKey, priceChangePercent);
            return priceChangePercent;
        } else {
            // 변화가 없으면 이전 값 사용
            return lastPriceChangeMap.getOrDefault(cacheKey, 0.0);
        }
    }
    
    /**
     * 거래량 변화율 계산
     */
    private double calculateVolumeChange(StandardExchangeData data, List<StandardExchangeData> history, String cacheKey) {
        if (history.size() < 2) {
            return lastVolumeChangeMap.getOrDefault(cacheKey, 0.0);
        }

        try {
            // 직전 데이터와 현재 데이터 비교
            double previousVolume = history.get(history.size() - 2).getVolume().doubleValue();
            double currentVolume = data.getVolume().doubleValue();
            
            // 거래량 변화가 있는 경우에만 새로 계산
            if (Math.abs(currentVolume - previousVolume) > 0.000001 && previousVolume > 0) {
                double volumeChangePercent = ((currentVolume - previousVolume) / previousVolume) * 100;
                // 새 값 저장
                lastVolumeChangeMap.put(cacheKey, volumeChangePercent);
                return volumeChangePercent;
            } else {
                // 변화가 없으면 이전 값 사용
                return lastVolumeChangeMap.getOrDefault(cacheKey, 0.0);
            }
        } catch (Exception e) {
            log.error("Error calculating volume change: {}", e.getMessage());
            return lastVolumeChangeMap.getOrDefault(cacheKey, 0.0);
        }
    }
    
    /**
     * 반등 확률 계산
     */
    private double calculateReboundProbability(double priceChangePercent, double volumeChangePercent, String cacheKey) {
        if (priceChangePercent < 0 && volumeChangePercent > 0) {
            // 가격 하락 + 거래량 증가 = 반등 가능성
            double reboundProbability = Math.min(Math.abs(priceChangePercent) * 0.5 + volumeChangePercent * 0.5, 100.0);
            // 새 값 저장
            lastReboundProbabilityMap.put(cacheKey, reboundProbability);
            return reboundProbability;
        } else {
            // 조건이 맞지 않으면 이전 값 사용
            return lastReboundProbabilityMap.getOrDefault(cacheKey, 0.0);
        }
    }
    
    /**
     * 기술적 지표 계산
     */
    private Map<String, Object> calculateIndicators(StandardExchangeData data, List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        // SMA 계산
        Map<String, Object> smaResults = indicatorService.calculateSMA(history, request);
        if (smaResults != null) {
            results.putAll(smaResults);
        }
        
        // RSI 계산
        Map<String, Object> rsiResults = indicatorService.calculateRSI(history, request);
        if (rsiResults != null) {
            results.putAll(rsiResults);
        }
        
        // 볼린저 밴드 계산
        Map<String, Object> bbResults = indicatorService.calculateBollingerBands(history, request);
        if (bbResults != null) {
            results.putAll(bbResults);
        }
        
        // 거래량 분석
        Map<String, Object> volumeResults = indicatorService.analyzeVolume(history);
        if (volumeResults != null) {
            results.putAll(volumeResults);
        }
        
        return results;
    }
    
    /**
     * 매수 신호 강도 계산
     */
    private double calculateBuySignalStrength(Map<String, Object> indicators, double priceChange, double volumeChange) {
        // 지표 값 추출
        double smaSignalStrength = (double) indicators.getOrDefault("smaSignalStrength", 50.0);
        double rsiSignalStrength = (double) indicators.getOrDefault("rsiSignalStrength", 50.0);
        double bbSignalStrength = (double) indicators.getOrDefault("bbSignalStrength", 50.0);
        
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
    
    /**
     * 시장 상태 판단 (과매수/과매도)
     */
    private Map<String, Object> determineMarketCondition(Map<String, Object> indicators) {
        Map<String, Object> result = new HashMap<>();
        String condition = "NEUTRAL";
        double strength = 50.0;
        
        // RSI 상태 확인
        String rsiSignal = (String) indicators.getOrDefault("rsiSignal", "NEUTRAL");
        double rsiValue = (double) indicators.getOrDefault("rsiValue", 50.0);
        
        // 볼린저 밴드 상태 확인
        String bbSignal = (String) indicators.getOrDefault("bollingerSignal", "MIDDLE_CROSS");
        
        // SMA 추세 확인
        String smaSignal = (String) indicators.getOrDefault("smaSignal", "NEUTRAL");
        
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
        if ("UPPER_TOUCH".equals(bbSignal) || "UPPER_HALF".equals(bbSignal)) {
            if ("OVERBOUGHT".equals(condition)) {
                strength += 10; // 과매수 신호 강화
            } else if ("NEUTRAL".equals(condition)) {
                condition = "OVERBOUGHT";
                strength = 15;
            }
        } else if ("LOWER_TOUCH".equals(bbSignal) || "LOWER_HALF".equals(bbSignal)) {
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
    
    /**
     * 분석 응답에 메타데이터 추가
     */
    private AnalysisResponse enrichResponseWithMetadata(AnalysisResponse response, String cardId) {
        return AnalysisResponse.builder()
            .exchange(response.getExchange())
            .currencyPair(response.getCurrencyPair())
            .symbol(response.getSymbol())
            .quoteCurrency(response.getQuoteCurrency())
            .analysisTime(response.getAnalysisTime())
            .currentPrice(response.getCurrentPrice())
            .priceChangePercent(response.getPriceChangePercent())
            .volumeChangePercent(response.getVolumeChangePercent())
            .reboundProbability(response.getReboundProbability())
            .analysisResult(response.getAnalysisResult())
            .message(response.getMessage())
            .tradingStyle(response.getTradingStyle())
            .cardId(cardId)
            .timestamp(System.currentTimeMillis())
            .sma1Difference(response.getSma1Difference())
            .smaMediumDifference(response.getSmaMediumDifference())
            .sma3Difference(response.getSma3Difference())
            .smaBreakout(response.isSmaBreakout())
            .smaSignal(response.getSmaSignal())
            .rsiValue(response.getRsiValue())
            .rsiSignal(response.getRsiSignal())
            .bollingerUpper(response.getBollingerUpper())
            .bollingerMiddle(response.getBollingerMiddle())
            .bollingerLower(response.getBollingerLower())
            .bollingerSignal(response.getBollingerSignal())
            .bollingerWidth(response.getBollingerWidth())
            .buySignalStrength(response.getBuySignalStrength())
            .marketCondition(response.getMarketCondition())
            .marketConditionStrength(response.getMarketConditionStrength())
            .build();
    }
    
    /**
     * 트레이딩 스타일에 따른 분석 간격 결정
     */
    private int getAnalysisInterval(String tradingStyle) {
        if (tradingStyle == null) {
            return 3; // 기본값: 3초
        }
        
        switch (tradingStyle.toLowerCase()) {
            case "scalping":
                return 1; // 초단타: 1초
            case "swing":
                return 5; // 스윙: 5초
            case "daytrading":
            default:
                return 3; // 단타: 3초
        }
    }
    
    /**
     * 구독 키 생성
     */
    private String getSubscriptionKey(String exchange, String currencyPair) {
        return exchange.toLowerCase() + "-" + currencyPair.toLowerCase();
    }
    
    /**
     * 지정된 키에 대한 구독 중지
     */
    private void stopSubscription(String subscriptionKey) {
        Disposable subscription = activeSubscriptions.remove(subscriptionKey);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Stopped existing subscription for key: {}", subscriptionKey);
        }
    }
    
    /**
     * 캐시된 데이터 정리
     */
    private void clearCachedData(String subscriptionKey) {
        lastPriceChangeMap.remove(subscriptionKey);
        lastVolumeChangeMap.remove(subscriptionKey);
        lastReboundProbabilityMap.remove(subscriptionKey);
    }
} 