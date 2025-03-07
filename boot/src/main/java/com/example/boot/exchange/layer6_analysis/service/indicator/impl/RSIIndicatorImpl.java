package com.example.boot.exchange.layer6_analysis.service.indicator.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.config.TradingStyleConfig;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.service.indicator.RSIIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * RSI 인디케이터 구현체
 */
@Slf4j
@Component
public class RSIIndicatorImpl implements RSIIndicator {

    private final TradingStyleConfig tradingStyleConfig;
    
    // 기본 파라미터 (트레이딩 스타일에 따라 변경됨)
    private int period = 14; // RSI 계산 기간
    private int overbought = 70; // 과매수 기준값
    private int oversold = 30; // 과매도 기준값
    
    @Autowired
    public RSIIndicatorImpl(TradingStyleConfig tradingStyleConfig) {
        this.tradingStyleConfig = tradingStyleConfig;
    }
    
    @Override
    public Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        if (history == null || history.size() < 2) {
            // 최소한 2개 이상의 데이터가 필요
            log.warn("RSI 계산을 위한 충분한 데이터가 없습니다. 필요: 최소 2개, 실제: {}", 
                     history != null ? history.size() : 0);
            results.put("value", 50.0);
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
            int adjustedPeriod = Math.min(period, dataSize - 1);
            
            // 최소 기간 설정
            adjustedPeriod = Math.max(adjustedPeriod, 2);
            
            log.debug("RSI 계산을 위한 조정된 기간: {}, 가용 데이터: {}", adjustedPeriod, dataSize);
            
            // 가격 데이터 추출
            List<Double> prices = history.stream()
                .map(data -> data.getPrice().doubleValue())
                .collect(Collectors.toList());
            
            // 조정된 기간으로 RSI 계산
            double rsi = calculateRSI(prices, adjustedPeriod);
            
            // 신호 결정
            String signal;
            if (rsi >= overbought) {
                signal = "OVERBOUGHT";
            } else if (rsi <= oversold) {
                signal = "OVERSOLD";
            } else {
                signal = "NEUTRAL";
            }
            
            // 신호 강도 계산
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
            // 기본값 설정
            results.put("value", 50.0);
            results.put("signal", "NEUTRAL");
            results.put("signalStrength", 50.0);
        }
        
        return results;
    }
    
    @Override
    public double calculateRSI(List<Double> prices, int period) {
        if (prices == null || prices.size() <= period) {
            return 50.0; // 충분한 데이터가 없으면 중립값 반환
        }
        
        try {
            // 가격 변화 계산
            List<Double> changes = new ArrayList<>();
            for (int i = 1; i < prices.size(); i++) {
                changes.add(prices.get(i) - prices.get(i - 1));
            }
            
            // 최근 period 개의 변화만 사용
            List<Double> recentChanges = changes.subList(Math.max(0, changes.size() - period), changes.size());
            
            // 상승/하락 구분
            double sumGain = 0;
            double sumLoss = 0;
            
            for (Double change : recentChanges) {
                if (change > 0) {
                    sumGain += change;
                } else {
                    sumLoss += Math.abs(change);
                }
            }
            
            // 평균 상승/하락 계산
            double avgGain = sumGain / recentChanges.size();
            double avgLoss = sumLoss / recentChanges.size();
            
            // RSI 계산
            if (avgLoss == 0) {
                return 100.0; // 하락이 없으면 RSI는 100
            }
            
            double rs = avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));
            
            return rsi;
        } catch (Exception e) {
            log.error("RSI 계산 중 오류 발생: {}", e.getMessage(), e);
            return 50.0; // 오류 발생 시 중립값 반환
        }
    }
    
    @Override
    public void setTradingStyleParameters(String tradingStyle) {
        if (tradingStyle == null) {
            // 기본값 사용
            period = 14;
            overbought = 70;
            oversold = 30;
            return;
        }
        
        switch (tradingStyle.toUpperCase()) {
            case "SCALPING":
                period = 7;
                overbought = 75;
                oversold = 25;
                break;
            case "DAY_TRADING":
            case "DAYTRADING":  // 대소문자 구분 없이 처리
                period = 14;
                overbought = 70;
                oversold = 30;
                break;
            case "SWING":
                period = 21;
                overbought = 65;
                oversold = 35;
                break;
            default:
                // 기본값 사용
                period = 14;
                overbought = 70;
                oversold = 30;
        }
        
        log.debug("RSI 파라미터 설정: tradingStyle={}, period={}, overbought={}, oversold={}",
            tradingStyle, period, overbought, oversold);
    }
} 