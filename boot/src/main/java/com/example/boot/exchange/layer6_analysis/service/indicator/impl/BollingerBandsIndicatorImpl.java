package com.example.boot.exchange.layer6_analysis.service.indicator.impl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.config.TradingStyleConfig;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.indicator.BollingerBandsIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * 볼린저 밴드 인디케이터 구현체
 */
@Slf4j
@Component
public class BollingerBandsIndicatorImpl implements BollingerBandsIndicator {

    private final TradingStyleConfig tradingStyleConfig;
    
    // 기본 파라미터 (트레이딩 스타일에 따라 변경됨)
    private int period = 20; // 볼린저 밴드 기간 (분)
    private double deviation = 2.0; // 표준편차 배수
    
    @Autowired
    public BollingerBandsIndicatorImpl(TradingStyleConfig tradingStyleConfig) {
        this.tradingStyleConfig = tradingStyleConfig;
    }
    
    @Override
    public Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        if (history == null || history.isEmpty()) {
            log.warn("볼린저 밴드 계산을 위한 데이터가 없습니다.");
            results.put("upper", 0.0);
            results.put("middle", 0.0);
            results.put("lower", 0.0);
            results.put("width", 0.0);
            results.put("position", "MIDDLE");
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
            return results;
        }
        
        try {
            // 트레이딩 스타일에 맞는 파라미터 설정
            setTradingStyleParameters(request.getTradingStyle());
            
            // 데이터 정렬 (시간 오름차순)
            history.sort(Comparator.comparing(StandardExchangeData::getTimestamp));
            
            // 가용한 데이터 수 확인
            int availableDataCount = history.size();
            
            // 데이터가 충분하지 않을 경우 간소화된 계산 방법 사용
            if (availableDataCount < period) {
                log.warn("볼린저 밴드 SMA 계산을 위한 충분한 데이터가 없습니다. 필요: {}, 실제: {}", period, availableDataCount);
                log.info("간소화된 볼린저 밴드 계산 방법을 사용합니다.");
                return calculateSimplifiedBollingerBands(history);
            }
            
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // 이동평균 계산
            double sum = 0;
            for (int i = history.size() - period; i < history.size(); i++) {
                sum += history.get(i).getPrice().doubleValue();
            }
            double middleBand = sum / period;
            
            // 표준편차 계산
            double sumSquaredDiff = 0;
            for (int i = history.size() - period; i < history.size(); i++) {
                double diff = history.get(i).getPrice().doubleValue() - middleBand;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);
            
            // 밴드 계산
            double upperBand = middleBand + (stdDev * deviation);
            double lowerBand = middleBand - (stdDev * deviation);
            
            // 밴드 폭 계산 (변동성 지표)
            double bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            
            // 현재 가격의 위치 결정
            String position;
            if (currentPrice >= upperBand) {
                position = "UPPER";
            } else if (currentPrice <= lowerBand) {
                position = "LOWER";
            } else if (currentPrice >= middleBand) {
                position = "UPPER_HALF";
            } else {
                position = "LOWER_HALF";
            }
            
            // 신호 결정
            String signal;
            if (currentPrice <= lowerBand) {
                signal = "OVERSOLD";
            } else if (currentPrice < middleBand && currentPrice > lowerBand) {
                signal = "BEARISH";
            } else if (currentPrice == middleBand) {
                signal = "NEUTRAL";
            } else if (currentPrice > middleBand && currentPrice < upperBand) {
                signal = "BULLISH";
            } else {
                signal = "OVERBOUGHT";
            }
            
            // 신호 강도 계산
            double signalStrength;
            if ("OVERSOLD".equals(signal)) {
                // 하단 밴드 터치는 강한 매수 신호
                signalStrength = 80.0 + (bandWidth / 5.0); // 80% ~ 100%
            } else if ("BEARISH".equals(signal)) {
                // 하단 절반은 중간 매수 신호
                double relativePosition = (currentPrice - lowerBand) / ((middleBand - lowerBand) / 2);
                signalStrength = 70.0 - (relativePosition * 10.0); // 60% ~ 70%
            } else if ("NEUTRAL".equals(signal)) {
                // 중간 밴드 교차는 중립 신호
                signalStrength = 50.0;
            } else if ("BULLISH".equals(signal)) {
                // 상단 절반은 중간 매도 신호
                double relativePosition = (currentPrice - middleBand) / ((upperBand - middleBand) / 2);
                signalStrength = 40.0 - (relativePosition * 10.0); // 30% ~ 40%
            } else { // OVERBOUGHT
                // 상단 밴드 터치는 강한 매도 신호
                signalStrength = 20.0 - (bandWidth / 5.0); // 0% ~ 20%
            }
            
            // 결과 저장
            results.put("upper", upperBand);
            results.put("middle", middleBand);
            results.put("lower", lowerBand);
            results.put("width", bandWidth);
            results.put("position", position);
            results.put("signal", signal);
            results.put("signalStrength", signalStrength);
            
            log.debug("볼린저 밴드 계산 결과: upper={}, middle={}, lower={}, width={}, position={}, signal={}, signalStrength={}",
                upperBand, middleBand, lowerBand, bandWidth, position, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("볼린저 밴드 계산 중 오류 발생: {}", e.getMessage(), e);
            
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            results.put("upper", currentPrice * 1.05);
            results.put("middle", currentPrice);
            results.put("lower", currentPrice * 0.95);
            results.put("width", 10.0);
            results.put("position", "MIDDLE");
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    // 데이터가 충분하지 않을 때 간소화된 계산 방법
    private Map<String, Object> calculateSimplifiedBollingerBands(List<StandardExchangeData> history) {
        Map<String, Object> results = new HashMap<>();
        
        // 현재 가격
        double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
        
        // 간단한 이동평균 계산 (모든 데이터 사용)
        double sum = 0;
        for (StandardExchangeData data : history) {
            sum += data.getPrice().doubleValue();
        }
        double middleBand = sum / history.size();
        
        // 간단한 표준편차 계산
        double sumSquaredDiff = 0;
        for (StandardExchangeData data : history) {
            double diff = data.getPrice().doubleValue() - middleBand;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / history.size());
        
        // 밴드 계산
        double upperBand = middleBand + (stdDev * 2.0);
        double lowerBand = middleBand - (stdDev * 2.0);
        
        // 밴드 폭 계산 (변동성 지표)
        double bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
        
        // 신호 결정
        String signal;
        if (currentPrice <= lowerBand) {
            signal = "OVERSOLD";
        } else if (currentPrice < middleBand && currentPrice > lowerBand) {
            signal = "BEARISH";
        } else if (Math.abs(currentPrice - middleBand) < 0.001) {
            signal = "NEUTRAL";
        } else if (currentPrice > middleBand && currentPrice < upperBand) {
            signal = "BULLISH";
        } else {
            signal = "OVERBOUGHT";
        }
        
        // 현재 가격의 위치 결정
        String position;
        if (currentPrice >= upperBand) {
            position = "UPPER";
        } else if (currentPrice <= lowerBand) {
            position = "LOWER";
        } else if (currentPrice >= middleBand) {
            position = "UPPER_HALF";
        } else {
            position = "LOWER_HALF";
        }
        
        // 신호 강도 계산
        double signalStrength;
        if ("OVERSOLD".equals(signal)) {
            signalStrength = 80.0;
        } else if ("BEARISH".equals(signal)) {
            signalStrength = 65.0;
        } else if ("NEUTRAL".equals(signal)) {
            signalStrength = 50.0;
        } else if ("BULLISH".equals(signal)) {
            signalStrength = 35.0;
        } else { // OVERBOUGHT
            signalStrength = 20.0;
        }
        
        // 결과 저장
        results.put("upper", upperBand);
        results.put("middle", middleBand);
        results.put("lower", lowerBand);
        results.put("width", bandWidth);
        results.put("position", position);
        results.put("signal", signal);
        results.put("signalStrength", signalStrength);
        
        log.info("간소화된 볼린저 밴드 계산 결과: upper={}, middle={}, lower={}, width={}, signal={}", 
                 upperBand, middleBand, lowerBand, bandWidth, signal);
        
        return results;
    }
    
    @Override
    public void setTradingStyleParameters(String tradingStyle) {
        if (tradingStyle == null) {
            // 기본값 사용
            period = 20;
            deviation = 2.0;
            return;
        }
        
        switch (tradingStyle.toUpperCase()) {
            case "SCALPING":
                period = 10;
                deviation = 2.5;
                break;
            case "DAY_TRADING":
            case "DAYTRADING":  // 대소문자 구분 없이 처리
                period = 20;
                deviation = 2.0;
                break;
            case "SWING":
                period = 40;
                deviation = 1.8;
                break;
            default:
                // 기본값 사용
                period = 20;
                deviation = 2.0;
        }
        
        log.debug("볼린저 밴드 파라미터 설정: tradingStyle={}, period={}, deviation={}", 
            tradingStyle, period, deviation);
    }
} 