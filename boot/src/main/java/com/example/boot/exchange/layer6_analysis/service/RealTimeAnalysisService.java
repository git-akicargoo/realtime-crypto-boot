package com.example.boot.exchange.layer6_analysis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.executor.TradingAnalysisExecutor;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeAnalysisService {
    
    private final TradingAnalysisExecutor executor;
    private final RedisCacheService cacheService;
    private final MarketAnalysisService marketAnalysisService;
    
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
            log.error("Invalid currency pair in request: {}", request);
            return Flux.error(new IllegalArgumentException("Invalid currency pair"));
        }
        
        log.debug("Subscribing to market data for {}-{}", request.getExchange(), currencyPair);
        
        // 초기 분석 결과 생성 (데이터가 없을 경우를 대비)
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
            .timestamp(timestamp)
            .tradingStyle(request.getTradingStyle())
            .build();
        
        // 초기 응답을 포함한 Flux 생성
        return Flux.concat(
            Flux.just(initialResponse),
            cacheService.subscribeToMarketData(request.getExchange(), currencyPair.toString())
                .doOnSubscribe(s -> log.debug("Subscribed to market data stream for {}-{}", request.getExchange(), currencyPair))
                .doOnNext(data -> log.debug("Received market data for analysis: {}", data))
                .map(latestData -> {
                    if (latestData == null) {
                        log.warn("Received null market data for {}-{}", request.getExchange(), currencyPair);
                        return null;
                    }

                    log.debug("Executing analysis for {}-{}", request.getExchange(), currencyPair);
                    try {
                        // TradingAnalysisExecutor를 통해 분석 실행
                        AnalysisResponse analysisResult = executor.executeAnalysis(
                            latestData,
                            request.getPriceDropThreshold(),
                            request.getVolumeIncreaseThreshold(),
                            request.getSmaShortPeriod(),
                            request.getSmaLongPeriod()
                        );
                        
                        log.debug("Analysis result: {}", analysisResult);
                        
                        // 카드 ID 설정
                        if (analysisResult.getCardId() == null) {
                            // 카드 ID만 설정하고 다른 필드는 그대로 유지
                            AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
                                .exchange(analysisResult.getExchange())
                                .currencyPair(analysisResult.getCurrencyPair())
                                .symbol(analysisResult.getSymbol())
                                .quoteCurrency(analysisResult.getQuoteCurrency())
                                .analysisTime(analysisResult.getAnalysisTime())
                                .currentPrice(analysisResult.getCurrentPrice())
                                .priceChangePercent(analysisResult.getPriceChangePercent())
                                .volumeChangePercent(analysisResult.getVolumeChangePercent())
                                .reboundProbability(analysisResult.getReboundProbability())
                                .analysisResult(analysisResult.getAnalysisResult())
                                .message(analysisResult.getMessage())
                                .tradingStyle(analysisResult.getTradingStyle())
                                .cardId(cardId)
                                .timestamp(System.currentTimeMillis());
                            
                            // SMA 관련 필드
                            builder.sma1Difference(analysisResult.getSma1Difference());
                            builder.smaMediumDifference(analysisResult.getSmaMediumDifference());
                            builder.sma3Difference(analysisResult.getSma3Difference());
                            builder.smaBreakout(analysisResult.isSmaBreakout());
                            builder.smaSignal(analysisResult.getSmaSignal());
                            
                            // RSI 관련 필드
                            builder.rsiValue(analysisResult.getRsiValue());
                            builder.rsiSignal(analysisResult.getRsiSignal());
                            
                            // 볼린저 밴드 관련 필드
                            builder.bollingerUpper(analysisResult.getBollingerUpper());
                            builder.bollingerMiddle(analysisResult.getBollingerMiddle());
                            builder.bollingerLower(analysisResult.getBollingerLower());
                            builder.bollingerWidth(analysisResult.getBollingerWidth());
                            builder.bollingerSignal(analysisResult.getBollingerSignal());
                            
                            // 매수 신호 강도
                            builder.buySignalStrength(analysisResult.getBuySignalStrength());
                            
                            // 시장 상태
                            builder.marketCondition(analysisResult.getMarketCondition());
                            builder.marketConditionStrength(analysisResult.getMarketConditionStrength());
                            
                            analysisResult = builder.build();
                            log.debug("Setting cardId in analysis result: {}", cardId);
                        }
                        
                        return analysisResult;
                    } catch (Exception e) {
                        log.error("Error executing analysis: {}", e.getMessage(), e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .doOnComplete(() -> log.info("Analysis stream completed for {}-{}", request.getExchange(), currencyPair))
                .doOnCancel(() -> log.info("Analysis stream cancelled for {}-{}", request.getExchange(), currencyPair))
                .doOnError(e -> log.error("Error in analysis stream for {}-{}: {}", 
                                        request.getExchange(), currencyPair, e.getMessage(), e))
        );
    }
    
    public void stopAnalysis(AnalysisRequest request) {
        log.info("Stopping analysis for request: {}", request);
        
        // 분석 중지
        executor.stopAnalysis(request.getExchange(), request.getCurrencyPair());
        
        // 구독 취소
        cacheService.unsubscribeFromMarketData(request.getExchange(), request.getCurrencyPair());
        
        log.info("Analysis stopped for exchange: {}, currencyPair: {}", 
                 request.getExchange(), request.getCurrencyPair());
    }
    
    private AnalysisResponse enrichResponse(AnalysisResponse response, AnalysisRequest request) {
        AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
            .exchange(response.getExchange())
            .currencyPair(response.getCurrencyPair())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(response.getAnalysisTime() != null ? response.getAnalysisTime() : LocalDateTime.now())
            .currentPrice(response.getCurrentPrice())
            .priceChangePercent(response.getPriceChangePercent())
            .volumeChangePercent(response.getVolumeChangePercent())
            .reboundProbability(response.getReboundProbability())
            .analysisResult(response.getAnalysisResult() != null ? response.getAnalysisResult() : "WAITING_FOR_DATA")
            .message(response.getMessage() != null ? response.getMessage() : "분석 데이터 수집 중...")
            .tradingStyle(request.getTradingStyle())
            .cardId(request.getCardId())
            .timestamp(request.getTimestamp());
            
        // SMA 관련 필드 설정 - 값이 있는 경우에만 포함
        if (response.getSma1Difference() != 0) {
            builder.sma1Difference(response.getSma1Difference());
        }
        if (response.getSmaMediumDifference() != 0) {
            builder.smaMediumDifference(response.getSmaMediumDifference());
        }
        if (response.getSma3Difference() != 0) {
            builder.sma3Difference(response.getSma3Difference());
        }
        
        // 불리언 값은 기본값이 false이므로 true인 경우에만 설정
        if (response.isSmaBreakout()) {
            builder.smaBreakout(true);
        }
        
        // 문자열 값은 null이 아닌 경우에만 설정
        if (response.getSmaSignal() != null) {
            builder.smaSignal(response.getSmaSignal());
        }
        
        // RSI 관련 필드 설정 - 값이 있는 경우에만 포함
        if (response.getRsiValue() != 0) {
            builder.rsiValue(response.getRsiValue());
        }
        if (response.getRsiSignal() != null) {
            builder.rsiSignal(response.getRsiSignal());
        }
        
        // 볼린저 밴드 관련 필드 설정 - 값이 있는 경우에만 포함
        if (response.getBollingerSignal() != null) {
            builder.bollingerSignal(response.getBollingerSignal());
        }
        if (response.getBollingerUpper() != 0) {
            builder.bollingerUpper(response.getBollingerUpper());
        }
        if (response.getBollingerMiddle() != 0) {
            builder.bollingerMiddle(response.getBollingerMiddle());
        }
        if (response.getBollingerLower() != 0) {
            builder.bollingerLower(response.getBollingerLower());
        }
        if (response.getBollingerWidth() != 0) {
            builder.bollingerWidth(response.getBollingerWidth());
        }
        
        // 매수 신호 강도 설정 - 값이 있는 경우에만 포함
        if (response.getBuySignalStrength() != 0) {
            builder.buySignalStrength(response.getBuySignalStrength());
        }
        
        // 시장 상태 설정 - 값이 있는 경우에만 포함
        if (response.getMarketCondition() != null) {
            builder.marketCondition(response.getMarketCondition());
        }
        if (response.getMarketConditionStrength() != 0) {
            builder.marketConditionStrength(response.getMarketConditionStrength());
        }
        
        return builder.build();
    }

    private AnalysisResponse enrichResponse(AnalysisResponse.AnalysisResponseBuilder builder, 
                                           StandardExchangeData data, 
                                           List<StandardExchangeData> history,
                                           AnalysisRequest request) {
        
        log.debug("Enriching analysis response with data: {}", data);
        
        // 기본 정보 설정
        builder.currentPrice(data.getPrice().doubleValue())
               .analysisTime(LocalDateTime.now())
               .timestamp(System.currentTimeMillis());
        
        // 가격 변화율 계산
        if (history.size() > 1) {
            double previousPrice = history.get(history.size() - 2).getPrice().doubleValue();
            double currentPrice = data.getPrice().doubleValue();
            double priceChangePercent = ((currentPrice - previousPrice) / previousPrice) * 100;
            builder.priceChangePercent(priceChangePercent);
            log.debug("Price change percent: {}", priceChangePercent);
        } else {
            builder.priceChangePercent(0.0);
        }
        
        // 거래량 변화율 계산
        if (history.size() > 1) {
            try {
                // 직접 거래량 변화율 계산
                double currentVolume = data.getVolume().doubleValue();
                double avgVolume = history.stream()
                    .limit(history.size() - 1)
                    .mapToDouble(d -> d.getVolume().doubleValue())
                    .average()
                    .orElse(0.0);
                
                double volumeChangePercent = 0.0;
                if (avgVolume > 0) {
                    volumeChangePercent = ((currentVolume - avgVolume) / avgVolume) * 100;
                }
                
                builder.volumeChangePercent(volumeChangePercent);
                log.debug("Volume change percent: {}", volumeChangePercent);
            } catch (Exception e) {
                log.error("Error calculating volume change: {}", e.getMessage());
                builder.volumeChangePercent(0.0);
            }
        } else {
            builder.volumeChangePercent(0.0);
        }
        
        // 반등 분석
        try {
            Map<String, Object> reboundResults = marketAnalysisService.analyzeRebound(data, history, request);
            if (reboundResults != null) {
                String analysisResult = (String) reboundResults.get("result");
                Double reboundProbability = (Double) reboundResults.get("probability");
                
                builder.analysisResult(analysisResult)
                       .reboundProbability(reboundProbability);
                
                // 추가 SMA 정보
                if (reboundResults.containsKey("smaShortDifference")) {
                    builder.sma1Difference((Double) reboundResults.get("smaShortDifference"));
                }
                if (reboundResults.containsKey("smaMediumDifference")) {
                    builder.smaMediumDifference((Double) reboundResults.get("smaMediumDifference"));
                }
                if (reboundResults.containsKey("smaLongDifference")) {
                    builder.sma3Difference((Double) reboundResults.get("smaLongDifference"));
                }
                if (reboundResults.containsKey("smaBreakout")) {
                    builder.smaBreakout((Boolean) reboundResults.get("smaBreakout"));
                }
                if (reboundResults.containsKey("smaSignal")) {
                    builder.smaSignal((String) reboundResults.get("smaSignal"));
                }
                
                // RSI 정보
                if (reboundResults.containsKey("rsiValue")) {
                    builder.rsiValue((Double) reboundResults.get("rsiValue"));
                }
                if (reboundResults.containsKey("rsiSignal")) {
                    builder.rsiSignal((String) reboundResults.get("rsiSignal"));
                }
                
                // 볼린저 밴드 정보
                if (reboundResults.containsKey("bollingerUpper")) {
                    builder.bollingerUpper((Double) reboundResults.get("bollingerUpper"));
                }
                if (reboundResults.containsKey("bollingerMiddle")) {
                    builder.bollingerMiddle((Double) reboundResults.get("bollingerMiddle"));
                }
                if (reboundResults.containsKey("bollingerLower")) {
                    builder.bollingerLower((Double) reboundResults.get("bollingerLower"));
                }
                if (reboundResults.containsKey("bollingerSignal")) {
                    builder.bollingerSignal((String) reboundResults.get("bollingerSignal"));
                }
                if (reboundResults.containsKey("bollingerWidth")) {
                    builder.bollingerWidth((Double) reboundResults.get("bollingerWidth"));
                }
                
                // 매수 신호 강도
                if (reboundResults.containsKey("buySignalStrength")) {
                    builder.buySignalStrength((Double) reboundResults.get("buySignalStrength"));
                }
                
                // 시장 상태
                if (reboundResults.containsKey("marketCondition")) {
                    builder.marketCondition((String) reboundResults.get("marketCondition"));
                }
                if (reboundResults.containsKey("marketConditionStrength")) {
                    builder.marketConditionStrength((Double) reboundResults.get("marketConditionStrength"));
                }
                
                // 메시지 생성
                String message = String.format(
                    "Price change: %.2f%%, Volume change: %.2f%%, Rebound probability: %.2f%%",
                    builder.build().getPriceChangePercent(),
                    builder.build().getVolumeChangePercent(),
                    reboundProbability
                );
                builder.message(message);
                
                log.debug("Analysis result: {}, Rebound probability: {}", analysisResult, reboundProbability);
            } else {
                log.warn("Rebound analysis returned null results");
                builder.analysisResult("INSUFFICIENT_DATA")
                       .message("분석에 필요한 충분한 데이터가 없습니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (Exception e) {
            log.error("Error in rebound analysis: {}", e.getMessage(), e);
            builder.analysisResult("ERROR")
                   .message("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
        
        return builder.build();
    }
} 