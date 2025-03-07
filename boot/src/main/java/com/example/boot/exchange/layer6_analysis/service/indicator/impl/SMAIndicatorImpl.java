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
import com.example.boot.exchange.layer6_analysis.service.indicator.SMAIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * SMA 인디케이터 구현체
 */
@Slf4j
@Component
public class SMAIndicatorImpl implements SMAIndicator {

    private final TradingStyleConfig tradingStyleConfig;
    
    // 기본 파라미터 (트레이딩 스타일에 따라 변경됨)
    private int shortPeriod = 5; // 단기 이동평균 기간 (분)
    private int mediumPeriod = 20; // 중기 이동평균 기간 (분)
    private int longPeriod = 60; // 장기 이동평균 기간 (분)
    
    @Autowired
    public SMAIndicatorImpl(TradingStyleConfig tradingStyleConfig) {
        this.tradingStyleConfig = tradingStyleConfig;
    }
    
    @Override
    public Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        if (history == null || history.size() < 2) {
            // 최소한 2개 이상의 데이터가 필요
            log.warn("SMA 계산을 위한 충분한 데이터가 없습니다. 필요: 최소 2개, 실제: {}", 
                     history != null ? history.size() : 0);
            results.put("shortDiff", 0.0);
            results.put("mediumDiff", 0.0);
            results.put("longDiff", 0.0);
            results.put("breakout", false);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
            return results;
        }
        
        try {
            // 트레이딩 스타일에 맞는 파라미터 설정
            setTradingStyleParameters(request.getTradingStyle());
            
            // 데이터 정렬 (시간 오름차순)
            history.sort(Comparator.comparing(StandardExchangeData::getTimestamp));
            
            // 가용한 데이터에 맞게 기간 조정
            int dataSize = history.size();
            int adjustedShortPeriod = Math.min(shortPeriod, dataSize / 2);
            int adjustedMediumPeriod = Math.min(mediumPeriod, dataSize / 3);
            int adjustedLongPeriod = Math.min(longPeriod, dataSize / 4);
            
            // 최소 기간 설정
            adjustedShortPeriod = Math.max(adjustedShortPeriod, 2);
            adjustedMediumPeriod = Math.max(adjustedMediumPeriod, 3);
            adjustedLongPeriod = Math.max(adjustedLongPeriod, 5);
            
            log.debug("SMA 계산을 위한 조정된 기간: 단기={}, 중기={}, 장기={}, 가용 데이터: {}", 
                     adjustedShortPeriod, adjustedMediumPeriod, adjustedLongPeriod, dataSize);
            
            // 조정된 기간으로 SMA 계산
            double shortSMA = calculateMovingAverage(history, adjustedShortPeriod);
            double mediumSMA = calculateMovingAverage(history, adjustedMediumPeriod);
            double longSMA = calculateMovingAverage(history, adjustedLongPeriod);
            
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // 차이 계산
            double shortDiff = ((currentPrice - shortSMA) / shortSMA) * 100;
            double mediumDiff = ((currentPrice - mediumSMA) / mediumSMA) * 100;
            double longDiff = ((currentPrice - longSMA) / longSMA) * 100;
            
            // 브레이크아웃 확인
            boolean breakout = (shortDiff > 0 && mediumDiff > 0 && longDiff > 0) || 
                              (shortDiff < 0 && mediumDiff < 0 && longDiff < 0);
            
            // 신호 결정
            String signal = determineSignal(shortDiff, mediumDiff, longDiff);
            
            // 신호 강도 계산
            double signalStrength = calculateSignalStrength(signal, shortDiff);
            
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
            // 기본값 설정
            results.put("shortDiff", 0.0);
            results.put("mediumDiff", 0.0);
            results.put("longDiff", 0.0);
            results.put("breakout", false);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    @Override
    public double calculateMovingAverage(List<StandardExchangeData> data, int period) {
        if (data == null || data.isEmpty()) {
            return 0.0;
        }
        
        int dataPoints = Math.min(period, data.size());
        if (dataPoints == 0) {
            return 0.0;
        }
        
        // 최근 데이터부터 계산
        return data.subList(data.size() - dataPoints, data.size()).stream()
                .mapToDouble(d -> d.getPrice().doubleValue())
                .average()
                .orElse(0.0);
    }
    
    @Override
    public double calculatePercentageChange(double current, double reference) {
        if (reference == 0) {
            return 0.0;
        }
        return ((current - reference) / reference) * 100;
    }
    
    @Override
    public void setTradingStyleParameters(String tradingStyle) {
        if (tradingStyle == null) {
            // 기본값 사용
            shortPeriod = 5;
            mediumPeriod = 15;
            longPeriod = 30;
            return;
        }
        
        switch (tradingStyle.toUpperCase()) {
            case "SCALPING":
                shortPeriod = 3;
                mediumPeriod = 10;
                longPeriod = 20;
                break;
            case "DAY_TRADING":
            case "DAYTRADING":  // 대소문자 구분 없이 처리
                shortPeriod = 5;
                mediumPeriod = 15;
                longPeriod = 30;
                break;
            case "SWING":
                shortPeriod = 10;
                mediumPeriod = 30;
                longPeriod = 60;
                break;
            default:
                // 기본값 사용
                shortPeriod = 5;
                mediumPeriod = 15;
                longPeriod = 30;
        }
        
        log.debug("SMA 파라미터 설정: tradingStyle={}, shortPeriod={}, mediumPeriod={}, longPeriod={}",
            tradingStyle, shortPeriod, mediumPeriod, longPeriod);
    }

    // 신호 결정 메서드
    private String determineSignal(double shortDiff, double mediumDiff, double longDiff) {
        // 모든 기간에서 상승 추세
        if (shortDiff > 1.0 && mediumDiff > 0.5 && longDiff > 0.2) {
            return "STRONG_UPTREND";
        }
        // 단기 및 중기 상승, 장기 중립/약상승
        else if (shortDiff > 0.5 && mediumDiff > 0.2 && longDiff >= 0) {
            return "UPTREND";
        }
        // 단기 상승, 중장기 중립
        else if (shortDiff > 0.2 && mediumDiff >= 0 && longDiff >= -0.2) {
            return "BULLISH";
        }
        // 모든 기간에서 하락 추세
        else if (shortDiff < -1.0 && mediumDiff < -0.5 && longDiff < -0.2) {
            return "STRONG_DOWNTREND";
        }
        // 단기 및 중기 하락, 장기 중립/약하락
        else if (shortDiff < -0.5 && mediumDiff < -0.2 && longDiff <= 0) {
            return "DOWNTREND";
        }
        // 단기 하락, 중장기 중립
        else if (shortDiff < -0.2 && mediumDiff <= 0 && longDiff <= 0.2) {
            return "BEARISH";
        }
        // 그 외 중립
        else {
            return "NEUTRAL";
        }
    }

    // 신호 강도 계산 메서드
    private double calculateSignalStrength(String signal, double shortDiff) {
        double signalStrength;
        if ("STRONG_UPTREND".equals(signal)) {
            signalStrength = 80.0 + Math.min(20.0, Math.abs(shortDiff)); // 80% ~ 100%
        } else if ("UPTREND".equals(signal)) {
            signalStrength = 70.0 + Math.min(10.0, Math.abs(shortDiff)); // 70% ~ 80%
        } else if ("BULLISH".equals(signal)) {
            signalStrength = 60.0 + Math.min(10.0, Math.abs(shortDiff)); // 60% ~ 70%
        } else if ("NEUTRAL".equals(signal)) {
            signalStrength = 50.0;
        } else if ("BEARISH".equals(signal)) {
            signalStrength = 40.0 - Math.min(10.0, Math.abs(shortDiff)); // 30% ~ 40%
        } else if ("DOWNTREND".equals(signal)) {
            signalStrength = 30.0 - Math.min(10.0, Math.abs(shortDiff)); // 20% ~ 30%
        } else { // STRONG_DOWNTREND
            signalStrength = 20.0 - Math.min(20.0, Math.abs(shortDiff)); // 0% ~ 20%
        }
        return signalStrength;
    }
} 