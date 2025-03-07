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
import com.example.boot.exchange.layer6_analysis.service.indicator.VolumeIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * 거래량 인디케이터 구현체
 */
@Slf4j
@Component
public class VolumeIndicatorImpl implements VolumeIndicator {

    private final TradingStyleConfig tradingStyleConfig;
    
    // 기본 파라미터 (트레이딩 스타일에 따라 변경됨)
    private int period = 20; // 거래량 비교 기간 (분)
    private double significantChangeThreshold = 50.0; // 유의미한 거래량 변화 기준 (%)
    
    @Autowired
    public VolumeIndicatorImpl(TradingStyleConfig tradingStyleConfig) {
        this.tradingStyleConfig = tradingStyleConfig;
    }
    
    @Override
    public Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        // 기본값 설정 (오류 발생 시 반환할 값)
        results.put("volumeChange", 0.0);
        results.put("signal", "NEUTRAL");
        results.put("signalStrength", 50.0);
        results.put("changePercent", 0.0);
        
        if (history == null || history.isEmpty()) {
            // 데이터가 없는 경우
            log.warn("거래량 분석을 위한 데이터가 없습니다.");
            return results;
        }
        
        // 단일 데이터만 있는 경우에도 기본 정보는 제공
        if (history.size() == 1) {
            log.info("거래량 분석을 위한 데이터가 하나뿐입니다. 현재 거래량: {}", 
                     history.get(0).getVolume());
            // 현재 거래량 정보는 제공
            results.put("currentVolume", history.get(0).getVolume().doubleValue());
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
            adjustedPeriod = Math.max(adjustedPeriod, 1);
            
            log.debug("거래량 분석을 위한 조정된 기간: {}, 가용 데이터: {}", adjustedPeriod, dataSize);
            
            // 현재 거래량
            double currentVolume = history.get(dataSize - 1).getVolume().doubleValue();
            
            // 이전 거래량 (조정된 기간에 따라)
            double previousVolume = 0.0;
            int previousDataCount = 0;
            
            for (int i = Math.max(0, dataSize - adjustedPeriod - 1); i < dataSize - 1; i++) {
                double volume = history.get(i).getVolume().doubleValue();
                if (volume > 0) {  // 0보다 큰 거래량만 고려
                    previousVolume += volume;
                    previousDataCount++;
                }
            }
            
            // 이전 거래량이 0이면 현재 거래량을 사용
            if (previousDataCount == 0 || previousVolume <= 0) {
                log.warn("이전 거래량 데이터가 없거나 0입니다. 현재 거래량을 사용합니다.");
                previousVolume = currentVolume > 0 ? currentVolume : 1.0;  // 0으로 나누기 방지
            } else {
                previousVolume /= previousDataCount;  // 평균 계산
            }
            
            // 거래량 변화율 계산
            double volumeChangePercent = 0.0;
            if (previousVolume > 0) {
                volumeChangePercent = ((currentVolume - previousVolume) / previousVolume) * 100;
            }
            
            log.info("거래량 변화율 계산: 현재 거래량={}, 이전 평균 거래량={}, 변화율={}%", 
                     currentVolume, previousVolume, volumeChangePercent);
            
            // 신호 결정
            String signal;
            if (volumeChangePercent >= 50) {
                signal = "STRONG_INCREASE";
            } else if (volumeChangePercent >= 20) {
                signal = "INCREASE";
            } else if (volumeChangePercent >= -20) {
                signal = "NEUTRAL";
            } else if (volumeChangePercent >= -50) {
                signal = "DECREASE";
            } else {
                signal = "STRONG_DECREASE";
            }
            
            // 신호 강도 계산
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
                signalStrength = 40.0;
            } else if (volumeChangePercent > -50) {
                // 거래량이 20-50% 감소하면 중간 부정 신호
                signalStrength = 30.0;
            } else {
                // 거래량이 50% 이상 감소하면 강한 부정 신호
                signalStrength = 20.0;
            }
            
            // 결과 저장
            results.put("volumeChange", volumeChangePercent);
            results.put("signal", signal);
            results.put("signalStrength", signalStrength);
            results.put("changePercent", volumeChangePercent);
            results.put("currentVolume", currentVolume);
            results.put("previousVolume", previousVolume);
            
            log.debug("거래량 분석 결과: volumeChange={}, signal={}, signalStrength={}", 
                     volumeChangePercent, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("거래량 분석 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    @Override
    public void setTradingStyleParameters(String tradingStyle) {
        if (tradingStyle == null) {
            // 기본값 사용
            period = 20;
            significantChangeThreshold = 50.0;
            return;
        }
        
        switch (tradingStyle.toUpperCase()) {
            case "SCALPING":
                period = 5;
                significantChangeThreshold = 30.0;
                break;
            case "DAY_TRADING":
            case "DAYTRADING":  // 대소문자 구분 없이 처리
                period = 20;
                significantChangeThreshold = 50.0;
                break;
            case "SWING":
                period = 60;
                significantChangeThreshold = 70.0;
                break;
            default:
                // 기본값 사용
                period = 20;
                significantChangeThreshold = 50.0;
        }
        
        log.debug("거래량 파라미터 설정: tradingStyle={}, period={}, significantChangeThreshold={}",
            tradingStyle, period, significantChangeThreshold);
    }
} 