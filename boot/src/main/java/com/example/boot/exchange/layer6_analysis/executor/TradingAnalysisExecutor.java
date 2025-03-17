package com.example.boot.exchange.layer6_analysis.executor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingAnalysisExecutor {
    private final MarketAnalysisService analysisService;
    private final RedisCacheService cacheService;
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    
    // 이전 변화율을 저장하는 맵
    private final Map<String, Double> lastPriceChangeMap = new ConcurrentHashMap<>();
    private final Map<String, Double> lastVolumeChangeMap = new ConcurrentHashMap<>();
    private final Map<String, Double> lastReboundProbabilityMap = new ConcurrentHashMap<>();
    
    public AnalysisResponse executeAnalysis(StandardExchangeData data, 
                                          double priceDropThreshold, 
                                          double volumeIncreaseThreshold,
                                          int smaShortPeriod,
                                          int smaLongPeriod) {
        try {
            // 히스토리 데이터 가져오기
            List<StandardExchangeData> history = cacheService.getAnalysisWindow(
                data.getExchange(), 
                data.getCurrencyPair().toString()
            );
            
            log.debug("Analysis history data size: {}", history.size());
            
            // 키 생성 (exchange-currencyPair)
            String key = data.getExchange() + "-" + data.getCurrencyPair().toString();
            
            // AnalysisRequest 객체 생성
            AnalysisRequest request = new AnalysisRequest();
            request.setExchange(data.getExchange());
            request.setCurrencyPair(data.getCurrencyPair().toString());
            request.setPriceDropThreshold(priceDropThreshold);
            request.setVolumeIncreaseThreshold(volumeIncreaseThreshold);
            request.setSmaShortPeriod(smaShortPeriod);
            request.setSmaLongPeriod(smaLongPeriod);
            
            // 기본 RSI 및 볼린저 밴드 설정
            request.setRsiPeriod(14);
            request.setRsiOverbought(70);
            request.setRsiOversold(30);
            request.setBollingerPeriod(20);
            request.setBollingerDeviation(2.0);
            
            // 중기 SMA 설정
            request.setSmaMediumPeriod(smaShortPeriod * 2);
            
            // 트레이딩 스타일 설정
            request.setTradingStyle("dayTrading");
            
            // CurrencyPair에서 symbol과 quoteCurrency 추출
            String currencyPairStr = data.getCurrencyPair().toString();
            if (currencyPairStr.contains("-")) {
                String[] parts = currencyPairStr.split("-");
                if (parts.length == 2) {
                    request.setQuoteCurrency(parts[0]);
                    request.setSymbol(parts[1]);
                }
            }
            
            // 가격 변화율 계산 - 이전 값 유지 로직 추가
            double priceChangePercent = 0.0;
            if (history.size() > 1) {
                // 직전 데이터와 현재 데이터 비교
                double previousPrice = history.get(history.size() - 2).getPrice().doubleValue();
                double currentPrice = data.getPrice().doubleValue();
                
                // 가격 변화가 있는 경우에만 새로 계산
                if (Math.abs(currentPrice - previousPrice) > 0.000001) {
                    priceChangePercent = ((currentPrice - previousPrice) / previousPrice) * 100;
                    // 새 값 저장
                    lastPriceChangeMap.put(key, priceChangePercent);
                    log.debug("Updated price change: {}%", priceChangePercent);
                } else {
                    // 변화가 없으면 이전 값 사용
                    priceChangePercent = lastPriceChangeMap.getOrDefault(key, 0.0);
                    log.debug("Using previous price change: {}%", priceChangePercent);
                }
            }
            
            // 거래량 변화율 계산 - 이전 값 유지 로직 추가
            double volumeChangePercent = 0.0;
            if (history.size() > 1) {
                try {
                    // 직전 데이터와 현재 데이터 비교
                    double previousVolume = history.get(history.size() - 2).getVolume().doubleValue();
                    double currentVolume = data.getVolume().doubleValue();
                    
                    // 거래량 변화가 있는 경우에만 새로 계산
                    if (Math.abs(currentVolume - previousVolume) > 0.000001 && previousVolume > 0) {
                        volumeChangePercent = ((currentVolume - previousVolume) / previousVolume) * 100;
                        // 새 값 저장
                        lastVolumeChangeMap.put(key, volumeChangePercent);
                        log.debug("Updated volume change: {}%", volumeChangePercent);
                    } else {
                        // 변화가 없으면 이전 값 사용
                        volumeChangePercent = lastVolumeChangeMap.getOrDefault(key, 0.0);
                        log.debug("Using previous volume change: {}%", volumeChangePercent);
                    }
                } catch (Exception e) {
                    log.error("Error calculating volume change: {}", e.getMessage());
                }
            }
            
            // 반등 확률 계산 - 이전 값 유지 로직 추가
            double reboundProbability = 0.0;
            if (priceChangePercent < 0 && volumeChangePercent > 0) {
                // 가격 하락 + 거래량 증가 = 반등 가능성
                reboundProbability = Math.min(Math.abs(priceChangePercent) * 0.5 + volumeChangePercent * 0.5, 100.0);
                // 새 값 저장
                lastReboundProbabilityMap.put(key, reboundProbability);
                log.debug("Updated rebound probability: {}%", reboundProbability);
            } else {
                // 조건이 맞지 않으면 이전 값 사용 (단, 이전 값이 있는 경우에만)
                reboundProbability = lastReboundProbabilityMap.getOrDefault(key, 0.0);
                log.debug("Using previous rebound probability: {}%", reboundProbability);
            }
            
            // 새로운 analyzeRebound 메서드 호출
            Map<String, Object> results = analysisService.analyzeRebound(data, history, request);
            
            if (results == null) {
                log.warn("Analysis returned null results");
                return AnalysisResponse.builder()
                    .exchange(data.getExchange())
                    .currencyPair(data.getCurrencyPair().toString())
                    .symbol(request.getSymbol())
                    .quoteCurrency(request.getQuoteCurrency())
                    .analysisResult("INSUFFICIENT_DATA")
                    .message("분석에 필요한 충분한 데이터가 없습니다.")
                    .priceChangePercent(priceChangePercent)
                    .volumeChangePercent(volumeChangePercent)
                    .reboundProbability(reboundProbability)
                    .analysisTime(java.time.LocalDateTime.now())
                    .build();
            }
            
            // 결과를 AnalysisResponse로 변환
            AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
                .exchange(data.getExchange())
                .currencyPair(data.getCurrencyPair().toString())
                .symbol(request.getSymbol())
                .quoteCurrency(request.getQuoteCurrency())
                .currentPrice(data.getPrice().doubleValue())
                .analysisResult((String) results.get("result"))
                .reboundProbability(reboundProbability)  // 계산된 반등 확률 사용
                .priceChangePercent(priceChangePercent)  // 계산된 가격 변화율 사용
                .volumeChangePercent(volumeChangePercent)  // 계산된 거래량 변화율 사용
                .tradingStyle(request.getTradingStyle())
                .analysisTime(java.time.LocalDateTime.now());  // 현재 시간 설정
            
            // SMA 관련 필드
            if (results.containsKey("smaShortDifference")) {
                builder.sma1Difference((Double) results.get("smaShortDifference"));
            }
            if (results.containsKey("smaMediumDifference")) {
                builder.smaMediumDifference((Double) results.get("smaMediumDifference"));
            }
            if (results.containsKey("smaLongDifference")) {
                builder.sma3Difference((Double) results.get("smaLongDifference"));
            }
            if (results.containsKey("smaBreakout")) {
                builder.smaBreakout((Boolean) results.get("smaBreakout"));
            }
            if (results.containsKey("smaSignal")) {
                builder.smaSignal((String) results.get("smaSignal"));
            }
            
            // RSI 관련 필드
            if (results.containsKey("rsiValue")) {
                builder.rsiValue((Double) results.get("rsiValue"));
            }
            if (results.containsKey("rsiSignal")) {
                builder.rsiSignal((String) results.get("rsiSignal"));
            }
            
            // 볼린저 밴드 관련 필드
            if (results.containsKey("bollingerUpper")) {
                builder.bollingerUpper((Double) results.get("bollingerUpper"));
            }
            if (results.containsKey("bollingerMiddle")) {
                builder.bollingerMiddle((Double) results.get("bollingerMiddle"));
            }
            if (results.containsKey("bollingerLower")) {
                builder.bollingerLower((Double) results.get("bollingerLower"));
            }
            if (results.containsKey("bollingerSignal")) {
                builder.bollingerSignal((String) results.get("bollingerSignal"));
            }
            if (results.containsKey("bollingerWidth")) {
                builder.bollingerWidth((Double) results.get("bollingerWidth"));
            }
            
            // 매수 신호 강도
            if (results.containsKey("buySignalStrength")) {
                builder.buySignalStrength((Double) results.get("buySignalStrength"));
            }
            
            // 시장 상태
            if (results.containsKey("marketCondition")) {
                builder.marketCondition((String) results.get("marketCondition"));
            }
            if (results.containsKey("marketConditionStrength")) {
                builder.marketConditionStrength((Double) results.get("marketConditionStrength"));
            }
            
            // 메시지 생성
            String message = String.format(
                "Price change: %.2f%%, Volume change: %.2f%%, Rebound probability: %.2f%%",
                priceChangePercent,
                volumeChangePercent,
                reboundProbability
            );
            builder.message(message);
            
            return builder.build();
        } catch (Exception e) {
            log.error("Analysis execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Analysis execution failed", e);
        }
    }

    public void stopAnalysis(String exchange, String currencyPair) {
        String key = getSubscriptionKey(exchange, currencyPair);
        Disposable subscription = activeSubscriptions.remove(key);
        
        // 분석 중지 시 저장된 변화율 정보도 제거
        lastPriceChangeMap.remove(key);
        lastVolumeChangeMap.remove(key);
        lastReboundProbabilityMap.remove(key);
        
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Stopped analysis for {}-{}", exchange, currencyPair);
        }
    }

    public void registerSubscription(String exchange, String currencyPair, Disposable subscription) {
        String key = getSubscriptionKey(exchange, currencyPair);
        Disposable oldSubscription = activeSubscriptions.put(key, subscription);
        
        if (oldSubscription != null && !oldSubscription.isDisposed()) {
            oldSubscription.dispose();
        }
    }

    private String getSubscriptionKey(String exchange, String currencyPair) {
        return exchange + "-" + currencyPair;
    }
} 