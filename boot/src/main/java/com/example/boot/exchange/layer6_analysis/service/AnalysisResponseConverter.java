package com.example.boot.exchange.layer6_analysis.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 분석 결과를 AnalysisResponse 객체로 변환하는 컨버터
 */
@Slf4j
@Component
public class AnalysisResponseConverter {

    /**
     * 분석 결과를 AnalysisResponse 객체로 변환
     * 
     * @param data 최신 시장 데이터
     * @param request 분석 요청
     * @param indicatorResults 기술 지표 계산 결과
     * @param priceChangePercent 가격 변화율
     * @param volumeChangePercent 거래량 변화율
     * @param reboundProbability 반등 확률
     * @param buySignalStrength 매수 신호 강도
     * @param marketCondition 시장 상태
     * @return 분석 응답 객체
     */
    public AnalysisResponse convertToAnalysisResponse(
            StandardExchangeData data, 
            AnalysisRequest request,
            Map<String, Object> indicatorResults,
            double priceChangePercent,
            double volumeChangePercent,
            double reboundProbability,
            double buySignalStrength,
            Map<String, Object> marketCondition) {
        
        // 분석 결과(매수/매도 신호) 결정
        String analysisResult = determineTradingSignal(buySignalStrength);
        
        // 메시지 생성
        String message = generateAnalysisMessage(priceChangePercent, volumeChangePercent, reboundProbability, 
                buySignalStrength, marketCondition, request.getTradingStyle());
        
        // AnalysisResponse 빌더 초기화
        AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
            .exchange(data.getExchange())
            .currencyPair(data.getCurrencyPair().toString())
            .symbol(request.getSymbol())
            .quoteCurrency(request.getQuoteCurrency())
            .analysisTime(LocalDateTime.now())
            .currentPrice(data.getPrice().doubleValue())
            .priceChangePercent(priceChangePercent)
            .volumeChangePercent(volumeChangePercent)
            .reboundProbability(reboundProbability)
            .analysisResult(analysisResult)
            .message(message)
            .tradingStyle(request.getTradingStyle())
            .buySignalStrength(buySignalStrength);
        
        // 시장 상태 설정
        if (marketCondition != null) {
            builder.marketCondition((String) marketCondition.get("condition"))
                  .marketConditionStrength((double) marketCondition.get("strength"));
        }
        
        // SMA 관련 필드 설정
        if (indicatorResults.containsKey("shortDiff")) {
            builder.sma1Difference((double) indicatorResults.get("shortDiff"));
        }
        if (indicatorResults.containsKey("mediumDiff")) {
            builder.smaMediumDifference((double) indicatorResults.get("mediumDiff"));
        }
        if (indicatorResults.containsKey("longDiff")) {
            builder.sma3Difference((double) indicatorResults.get("longDiff"));
        }
        if (indicatorResults.containsKey("breakout")) {
            builder.smaBreakout((boolean) indicatorResults.get("breakout"));
        }
        if (indicatorResults.containsKey("smaSignal")) {
            builder.smaSignal((String) indicatorResults.get("smaSignal"));
        }
        
        // RSI 관련 필드 설정
        if (indicatorResults.containsKey("rsiValue")) {
            builder.rsiValue((double) indicatorResults.get("rsiValue"));
        }
        if (indicatorResults.containsKey("rsiSignal")) {
            builder.rsiSignal((String) indicatorResults.get("rsiSignal"));
        }
        
        // 볼린저 밴드 관련 필드 설정
        if (indicatorResults.containsKey("bollingerUpper")) {
            builder.bollingerUpper((double) indicatorResults.get("bollingerUpper"));
        }
        if (indicatorResults.containsKey("bollingerMiddle")) {
            builder.bollingerMiddle((double) indicatorResults.get("bollingerMiddle"));
        }
        if (indicatorResults.containsKey("bollingerLower")) {
            builder.bollingerLower((double) indicatorResults.get("bollingerLower"));
        }
        if (indicatorResults.containsKey("bollingerWidth")) {
            builder.bollingerWidth((double) indicatorResults.get("bollingerWidth"));
        }
        if (indicatorResults.containsKey("bollingerSignal")) {
            builder.bollingerSignal((String) indicatorResults.get("bollingerSignal"));
        }
        
        return builder.build();
    }
    
    /**
     * 매수 신호 강도에 따른 트레이딩 신호 결정
     */
    private String determineTradingSignal(double buySignalStrength) {
        if (buySignalStrength >= 80) return "STRONG_BUY";
        if (buySignalStrength >= 60) return "BUY";
        if (buySignalStrength >= 40) return "NEUTRAL";
        if (buySignalStrength >= 20) return "SELL";
        return "STRONG_SELL";
    }
    
    /**
     * 종합 분석 메시지 생성
     */
    private String generateAnalysisMessage(
            double priceChangePercent, 
            double volumeChangePercent, 
            double reboundProbability, 
            double buySignalStrength,
            Map<String, Object> marketCondition,
            String tradingStyle) {
        
        String condition = (String) marketCondition.get("condition");
        double strength = (double) marketCondition.get("strength");
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("트레이딩 스타일: %s, ", formatTradingStyle(tradingStyle)));
        message.append(String.format("매수 신호 강도: %.1f%%, ", buySignalStrength));
        
        if ("OVERBOUGHT".equals(condition)) {
            message.append(String.format("과매수 상태 (강도: %.1f%%)", strength));
        } else if ("OVERSOLD".equals(condition)) {
            message.append(String.format("과매도 상태 (강도: %.1f%%)", strength));
        } else {
            message.append("중립 상태");
        }
        
        message.append(String.format(", 가격 변화: %.2f%%, 거래량 변화: %.2f%%", priceChangePercent, volumeChangePercent));
        
        if (reboundProbability > 20) {
            message.append(String.format(", 반등 확률: %.1f%%", reboundProbability));
        }
        
        return message.toString();
    }
    
    /**
     * 트레이딩 스타일 포맷팅
     */
    private String formatTradingStyle(String style) {
        if (style == null) {
            return "단타"; // 기본값 설정
        }
        
        switch (style.toUpperCase()) {
            case "SCALPING": return "초단타";
            case "DAY_TRADING": return "단타";
            case "SWING": return "스윙";
            default: return style;
        }
    }
} 