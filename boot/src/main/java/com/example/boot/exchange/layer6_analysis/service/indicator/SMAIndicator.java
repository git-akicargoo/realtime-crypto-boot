package com.example.boot.exchange.layer6_analysis.service.indicator;

import java.util.List;
import java.util.Map;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;

/**
 * 단순 이동 평균(SMA) 계산을 위한 인터페이스
 */
public interface SMAIndicator {
    
    /**
     * SMA 지표 계산
     * 
     * @param history 가격 데이터 히스토리
     * @param request 분석 요청 파라미터
     * @return 계산된 SMA 지표 결과 맵 (shortDiff, mediumDiff, longDiff, signal, signalStrength 등)
     */
    Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request);
    
    /**
     * 지정된 기간에 대한 이동 평균 계산
     * 
     * @param data 가격 데이터
     * @param period 기간(초)
     * @return 계산된 이동 평균
     */
    double calculateMovingAverage(List<StandardExchangeData> data, int period);
    
    /**
     * 현재 가격과 참조 가격의 백분율 차이 계산
     * 
     * @param current 현재 가격
     * @param reference 참조 가격
     * @return 백분율 차이
     */
    double calculatePercentageChange(double current, double reference);
    
    /**
     * 트레이딩 스타일에 맞는 파라미터 설정
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     */
    void setTradingStyleParameters(String tradingStyle);
} 