package com.example.boot.exchange.layer6_analysis.service.indicator;

import java.util.List;
import java.util.Map;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;

/**
 * 상대 강도 지수(RSI) 계산을 위한 인터페이스
 */
public interface RSIIndicator {
    
    /**
     * RSI 지표 계산
     * 
     * @param history 가격 데이터 히스토리
     * @param request 분석 요청 파라미터
     * @return 계산된 RSI 지표 결과 맵 (value, signal, signalStrength 등)
     */
    Map<String, Object> calculate(List<StandardExchangeData> history, AnalysisRequest request);
    
    /**
     * RSI 값 계산
     * 
     * @param prices 가격 데이터 리스트
     * @param period RSI 계산 기간
     * @return 계산된 RSI 값
     */
    double calculateRSI(List<Double> prices, int period);
    
    /**
     * 트레이딩 스타일에 맞는 파라미터 설정
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     */
    void setTradingStyleParameters(String tradingStyle);
} 