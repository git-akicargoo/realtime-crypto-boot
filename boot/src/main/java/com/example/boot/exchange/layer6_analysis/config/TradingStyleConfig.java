package com.example.boot.exchange.layer6_analysis.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;

@Component
public class TradingStyleConfig {
    
    // 트레이딩 스타일별 가중치 설정
    private final Map<String, Map<String, Double>> styleWeights = new HashMap<>();
    
    // 트레이딩 스타일별 기본 파라미터 설정
    private final Map<String, Map<String, Object>> styleParameters = new HashMap<>();
    
    public TradingStyleConfig() {
        initializeWeights();
        initializeParameters();
    }
    
    private void initializeWeights() {
        // 초단타 (Scalping) 가중치
        Map<String, Double> scalpingWeights = new HashMap<>();
        scalpingWeights.put("rsi", 0.4);
        scalpingWeights.put("bollinger", 0.3);
        scalpingWeights.put("sma", 0.2);
        scalpingWeights.put("volume", 0.1);
        styleWeights.put("scalping", scalpingWeights);
        
        // 단타 (Day Trading) 가중치
        Map<String, Double> dayTradingWeights = new HashMap<>();
        dayTradingWeights.put("sma", 0.35);
        dayTradingWeights.put("rsi", 0.3);
        dayTradingWeights.put("bollinger", 0.25);
        dayTradingWeights.put("volume", 0.1);
        styleWeights.put("dayTrading", dayTradingWeights);
        
        // 스윙 (Swing Trading) 가중치
        Map<String, Double> swingWeights = new HashMap<>();
        swingWeights.put("sma", 0.4);
        swingWeights.put("bollinger", 0.3);
        swingWeights.put("rsi", 0.2);
        swingWeights.put("volume", 0.1);
        styleWeights.put("swing", swingWeights);
    }
    
    private void initializeParameters() {
        // 초단타 (Scalping) 파라미터
        Map<String, Object> scalpingParams = new HashMap<>();
        scalpingParams.put("smaShortPeriod", 1);
        scalpingParams.put("smaMediumPeriod", 3);
        scalpingParams.put("smaLongPeriod", 5);
        scalpingParams.put("rsiPeriod", 7);
        scalpingParams.put("rsiOverbought", 80);
        scalpingParams.put("rsiOversold", 20);
        scalpingParams.put("bollingerPeriod", 10);
        scalpingParams.put("bollingerDeviation", 2.0);
        scalpingParams.put("volumePeriod", 5); // 5분
        styleParameters.put("scalping", scalpingParams);
        
        // 단타 (Day Trading) 파라미터
        Map<String, Object> dayTradingParams = new HashMap<>();
        dayTradingParams.put("smaShortPeriod", 5);
        dayTradingParams.put("smaMediumPeriod", 15);
        dayTradingParams.put("smaLongPeriod", 30);
        dayTradingParams.put("rsiPeriod", 14);
        dayTradingParams.put("rsiOverbought", 70);
        dayTradingParams.put("rsiOversold", 30);
        dayTradingParams.put("bollingerPeriod", 20);
        dayTradingParams.put("bollingerDeviation", 2.0);
        dayTradingParams.put("volumePeriod", 30); // 30분
        styleParameters.put("dayTrading", dayTradingParams);
        
        // 스윙 (Swing Trading) 파라미터
        Map<String, Object> swingParams = new HashMap<>();
        swingParams.put("smaShortPeriod", 60); // 1시간
        swingParams.put("smaMediumPeriod", 240); // 4시간
        swingParams.put("smaLongPeriod", 1440); // 1일
        swingParams.put("rsiPeriod", 21);
        swingParams.put("rsiOverbought", 70);
        swingParams.put("rsiOversold", 30);
        swingParams.put("bollingerPeriod", 50);
        swingParams.put("bollingerDeviation", 2.5);
        swingParams.put("volumePeriod", 1440); // 24시간
        styleParameters.put("swing", swingParams);
    }
    
    public Map<String, Double> getWeightsForStyle(String tradingStyle) {
        return styleWeights.getOrDefault(tradingStyle, styleWeights.get("dayTrading"));
    }
    
    public Map<String, Object> getParametersForStyle(String tradingStyle) {
        return styleParameters.getOrDefault(tradingStyle, styleParameters.get("dayTrading"));
    }
    
    public void applyStyleParameters(AnalysisRequest request) {
        String style = request.getTradingStyle();
        Map<String, Object> params = getParametersForStyle(style);
        
        // SMA 파라미터 적용
        request.setSmaShortPeriod((int) params.get("smaShortPeriod"));
        request.setSmaMediumPeriod((int) params.get("smaMediumPeriod"));
        request.setSmaLongPeriod((int) params.get("smaLongPeriod"));
        
        // RSI 파라미터 적용
        request.setRsiPeriod((int) params.get("rsiPeriod"));
        request.setRsiOverbought((int) params.get("rsiOverbought"));
        request.setRsiOversold((int) params.get("rsiOversold"));
        
        // 볼린저 밴드 파라미터 적용
        request.setBollingerPeriod((int) params.get("bollingerPeriod"));
        request.setBollingerDeviation((double) params.get("bollingerDeviation"));
    }
}