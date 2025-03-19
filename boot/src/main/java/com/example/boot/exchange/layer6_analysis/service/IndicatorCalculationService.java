package com.example.boot.exchange.layer6_analysis.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * 기술적 지표 계산을 담당하는 서비스
 * SMA, RSI, 볼린저 밴드 등 여러 지표의 계산 로직을 포함
 */
@Slf4j
@Service
public class IndicatorCalculationService {

    /**
     * SMA(Simple Moving Average) 계산
     * @param history 가격 히스토리 데이터
     * @param request 분석 요청 객체
     * @return SMA 계산 결과 맵
     */
    public Map<String, Object> calculateSMA(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("SMA 계산: 최소 2개 이상의 데이터가 필요합니다. (데이터 수: {})", history.size());
            return results;
        }
        
        try {
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // SMA 기간 설정
            int shortPeriod = request.getSmaShortPeriod() * 60; // 분 단위로 변환
            int mediumPeriod = request.getSmaMediumPeriod() * 60;
            int longPeriod = request.getSmaLongPeriod() * 60;
            
            // 단기, 중기, 장기 SMA 계산
            double shortSMA = 0.0;
            double mediumSMA = 0.0;
            double longSMA = 0.0;
            
            // 단순 평균 계산 (데이터가 적을 경우)
            double simpleAvg = history.stream()
                .mapToDouble(d -> d.getPrice().doubleValue())
                .average()
                .orElse(currentPrice);
            
            // 데이터가 충분하지 않을 경우 간소화된 계산
            if (history.size() < shortPeriod) {
                log.debug("단기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                shortSMA = simpleAvg;
            } else {
                shortSMA = calculateMovingAverage(history, shortPeriod);
            }
            
            if (history.size() < mediumPeriod) {
                log.debug("중기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                // 중기 SMA가 없어도 단기 SMA로 계산 가능
                mediumSMA = shortSMA;
            } else {
                mediumSMA = calculateMovingAverage(history, mediumPeriod);
            }
            
            if (history.size() < longPeriod) {
                log.debug("장기 SMA 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                // 장기 SMA가 없어도 중기 SMA로 계산 가능
                longSMA = mediumSMA;
            } else {
                longSMA = calculateMovingAverage(history, longPeriod);
            }
            
            // 현재 가격과 각 SMA의 차이 계산 (%)
            double shortDiff = calculatePercentageChange(currentPrice, shortSMA);
            double mediumDiff = calculatePercentageChange(currentPrice, mediumSMA);
            double longDiff = calculatePercentageChange(currentPrice, longSMA);
            
            // SMA 돌파 여부 확인
            boolean breakout = isSMABreakout(shortDiff, longDiff);
            
            // SMA 신호 결정
            String signal = calculateSMASignal(shortDiff, mediumDiff, longDiff);
            
            // SMA 매수 신호 강도 계산 (0-100%)
            double signalStrength = calculateSMASignalStrength(signal, shortDiff);
            
            // 결과 저장
            results.put("shortDiff", shortDiff);
            results.put("mediumDiff", mediumDiff);
            results.put("longDiff", longDiff);
            results.put("breakout", breakout);
            results.put("smaSignal", signal);
            results.put("smaSignalStrength", signalStrength);
            
            log.debug("SMA 계산 결과: shortDiff={}, mediumDiff={}, longDiff={}, breakout={}, signal={}, signalStrength={}",
                shortDiff, mediumDiff, longDiff, breakout, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("SMA 계산 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * RSI(Relative Strength Index) 계산
     * @param history 가격 히스토리 데이터
     * @param request 분석 요청 객체
     * @return RSI 계산 결과 맵
     */
    public Map<String, Object> calculateRSI(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getRsiPeriod();
        int overbought = request.getRsiOverbought();
        int oversold = request.getRsiOversold();
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("RSI 계산: 최소 2개 이상의 데이터가 필요합니다. (현재: {})", history.size());
            return results;
        }
        
        try {
            // RSI 계산을 위한 가격 데이터 추출
            List<Double> prices = history.stream()
                                       .map(data -> data.getPrice().doubleValue())
                                       .collect(Collectors.toList());
            
            // 간소화된 RSI 계산 (데이터가 적을 경우)
            double rsi;
            if (history.size() < period + 1) {
                log.debug("RSI 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                rsi = calculateSimplifiedRSI(prices);
            } else {
                // 충분한 데이터가 있으면 정상 RSI 계산
                rsi = calculateRSI(prices, period);
            }
            
            // RSI 신호 결정
            String signal;
            if (rsi >= overbought) {
                signal = "OVERBOUGHT";
            } else if (rsi <= oversold) {
                signal = "OVERSOLD";
            } else {
                signal = "NEUTRAL";
            }
            
            // RSI 매수 신호 강도 계산 (0-100%)
            double signalStrength = calculateRSISignalStrength(rsi, oversold, overbought);
            
            // 결과 저장
            results.put("rsiValue", rsi);
            results.put("rsiSignal", signal);
            results.put("rsiSignalStrength", signalStrength);
            
            log.debug("RSI 계산 결과: value={}, signal={}, signalStrength={}", rsi, signal, signalStrength);
            
        } catch (Exception e) {
            log.error("RSI 계산 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * 볼린저 밴드 계산
     * @param history 가격 히스토리 데이터
     * @param request 분석 요청 객체
     * @return 볼린저 밴드 계산 결과 맵
     */
    public Map<String, Object> calculateBollingerBands(List<StandardExchangeData> history, AnalysisRequest request) {
        Map<String, Object> results = new HashMap<>();
        
        int period = request.getBollingerPeriod();
        double deviation = request.getBollingerDeviation();
        
        // 데이터가 최소 2개 이상 있으면 계산 시도
        if (history.size() < 2) {
            log.warn("볼린저 밴드 계산: 최소 2개 이상의 데이터가 필요합니다. (현재: {})", history.size());
            return results;
        }
        
        try {
            // 현재 가격
            double currentPrice = history.get(history.size() - 1).getPrice().doubleValue();
            
            // 간소화된 볼린저 밴드 계산 (데이터가 적을 경우)
            double middleBand, upperBand, lowerBand, bandWidth;
            
            if (history.size() < period) {
                log.debug("볼린저 밴드 계산: 제한된 데이터로 간소화된 계산을 수행합니다. (데이터 수: {})", history.size());
                
                // 단순 이동평균 계산
                double sum = 0;
                for (StandardExchangeData data : history) {
                    sum += data.getPrice().doubleValue();
                }
                middleBand = sum / history.size();
                
                // 단순 표준편차 계산
                double sumSquaredDiff = 0;
                for (StandardExchangeData data : history) {
                    double diff = data.getPrice().doubleValue() - middleBand;
                    sumSquaredDiff += diff * diff;
                }
                double stdDev = Math.sqrt(sumSquaredDiff / history.size());
                
                // 밴드 계산
                upperBand = middleBand + (stdDev * deviation);
                lowerBand = middleBand - (stdDev * deviation);
                bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            } else {
                // 충분한 데이터가 있으면 정상 볼린저 밴드 계산
                middleBand = calculateMovingAverage(history, period * 60);
                
                // 표준 편차 계산
                double sum = 0;
                for (int i = Math.max(0, history.size() - period); i < history.size(); i++) {
                    double price = history.get(i).getPrice().doubleValue();
                    sum += Math.pow(price - middleBand, 2);
                }
                double stdDev = Math.sqrt(sum / Math.min(period, history.size()));
                
                // 상단 및 하단 밴드
                upperBand = middleBand + (stdDev * deviation);
                lowerBand = middleBand - (stdDev * deviation);
                
                // 밴드 폭 (변동성 지표)
                bandWidth = ((upperBand - lowerBand) / middleBand) * 100;
            }
            
            // 볼린저 밴드 신호 결정
            String signal = determineBollingerSignal(currentPrice, upperBand, middleBand, lowerBand);
            
            // 볼린저 밴드 매수 신호 강도 계산 (0-100%)
            double signalStrength = calculateBollingerSignalStrength(signal, currentPrice, upperBand, middleBand, lowerBand, bandWidth);
            
            // 결과 저장
            results.put("bollingerUpper", upperBand);
            results.put("bollingerMiddle", middleBand);
            results.put("bollingerLower", lowerBand);
            results.put("bollingerWidth", bandWidth);
            results.put("bollingerSignal", signal);
            results.put("bbSignalStrength", signalStrength);
            
            log.debug("볼린저 밴드 계산 결과: upper={}, middle={}, lower={}, width={}, signal={}, signalStrength={}",
                upperBand, middleBand, lowerBand, bandWidth, signal, signalStrength);
                
        } catch (Exception e) {
            log.error("볼린저 밴드 계산 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * 거래량 분석
     * @param history 가격 히스토리 데이터
     * @return 거래량 분석 결과 맵
     */
    public Map<String, Object> analyzeVolume(List<StandardExchangeData> history) {
        Map<String, Object> results = new HashMap<>();
        
        // 충분한 데이터가 없으면 기본값 반환
        if (history.size() < 10) {
            results.put("volumeChangePercent", 0.0);
            results.put("volumeSignalStrength", 50.0);
            return results;
        }
        
        try {
            // 현재 거래량
            double currentVolume = history.get(history.size() - 1).getVolume().doubleValue();
            
            // 이전 거래량 평균 (최근 10개 데이터)
            double avgVolume = history.subList(history.size() - 10, history.size() - 1).stream()
                .mapToDouble(data -> data.getVolume().doubleValue())
                .average()
                .orElse(0.0);
            
            // 거래량 변화율 계산
            double volumeChangePercent = 0.0;
            if (avgVolume > 0) {
                volumeChangePercent = ((currentVolume - avgVolume) / avgVolume) * 100;
            }
            
            // 거래량 신호 강도 계산 (0-100%)
            double signalStrength = calculateVolumeSignalStrength(volumeChangePercent);
            
            // 결과 저장
            results.put("volumeChangePercent", volumeChangePercent);
            results.put("volumeSignalStrength", signalStrength);
            
            log.debug("거래량 분석 결과: changePercent={}, signalStrength={}", volumeChangePercent, signalStrength);
            
        } catch (Exception e) {
            log.error("거래량 분석 중 오류 발생: {}", e.getMessage(), e);
            results.put("volumeChangePercent", 0.0);
            results.put("volumeSignalStrength", 50.0);
        }
        
        return results;
    }
    
    /**
     * 단순 이동평균 계산
     */
    private double calculateMovingAverage(List<StandardExchangeData> data, int seconds) {
        int dataPoints = Math.min(seconds, data.size());
        if (dataPoints == 0) return 0.0;
        
        return data.subList(data.size() - dataPoints, data.size()).stream()
            .mapToDouble(d -> d.getPrice().doubleValue())
            .average()
            .orElse(0.0);
    }
    
    /**
     * 백분율 변화 계산
     */
    private double calculatePercentageChange(double current, double reference) {
        if (reference == 0) return 0.0;
        return ((current - reference) / reference) * 100;
    }
    
    /**
     * SMA 돌파 여부 확인
     */
    private boolean isSMABreakout(double shortDiff, double longDiff) {
        // 단기 이평선이 장기 이평선을 상향돌파하는 경우
        return shortDiff > 0 && longDiff < 0;
    }
    
    /**
     * SMA 신호 계산
     */
    private String calculateSMASignal(double shortDiff, double mediumDiff, double longDiff) {
        if (shortDiff > 0 && mediumDiff > 0 && longDiff > 0) {
            return "STRONG_UPTREND"; // 강한 상승 추세
        } else if (shortDiff > 0 && mediumDiff > 0) {
            return "UPTREND"; // 상승 추세
        } else if (shortDiff < 0 && mediumDiff < 0 && longDiff < 0) {
            return "STRONG_DOWNTREND"; // 강한 하락 추세
        } else if (shortDiff < 0 && mediumDiff < 0) {
            return "DOWNTREND"; // 하락 추세
        } else if (shortDiff > 0 && mediumDiff < 0) {
            return "BULLISH"; // 단기 상승 (매수 신호)
        } else if (shortDiff < 0 && mediumDiff > 0) {
            return "BEARISH"; // 단기 하락 (매도 신호)
        } else {
            return "NEUTRAL"; // 중립
        }
    }
    
    /**
     * SMA 신호 강도 계산
     */
    private double calculateSMASignalStrength(String signal, double shortDiff) {
        double signalStrength;
        
        if ("STRONG_UPTREND".equals(signal)) {
            signalStrength = 80.0 + Math.min(20.0, shortDiff); // 80% ~ 100%
        } else if ("UPTREND".equals(signal)) {
            signalStrength = 70.0 + Math.min(10.0, shortDiff); // 70% ~ 80%
        } else if ("BULLISH".equals(signal)) {
            signalStrength = 60.0 + Math.min(10.0, shortDiff); // 60% ~ 70%
        } else if ("NEUTRAL".equals(signal)) {
            signalStrength = 50.0;
        } else if ("BEARISH".equals(signal)) {
            signalStrength = 40.0 - Math.min(10.0, Math.abs(shortDiff)); // 30% ~ 40%
        } else if ("DOWNTREND".equals(signal)) {
            signalStrength = 30.0 - Math.min(10.0, Math.abs(shortDiff)); // 20% ~ 30%
        } else { // STRONG_DOWNTREND
            signalStrength = 20.0 - Math.min(20.0, Math.abs(shortDiff)); // 0% ~ 20%
        }
        
        return Math.max(0, Math.min(100, signalStrength));
    }
    
    /**
     * 간소화된 RSI 계산 (데이터가 적을 경우)
     */
    private double calculateSimplifiedRSI(List<Double> prices) {
        if (prices.size() < 2) return 50.0;
        
        // 가격 변화 계산
        double totalGain = 0;
        double totalLoss = 0;
        int count = 0;
        
        for (int i = 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                totalGain += change;
            } else {
                totalLoss += Math.abs(change);
            }
            count++;
        }
        
        // 평균 이득/손실 계산
        double avgGain = totalGain / count;
        double avgLoss = totalLoss / count;
        
        // RSI 계산
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    /**
     * RSI 계산
     */
    private double calculateRSI(List<Double> prices, int period) {
        if (prices.size() <= period) {
            return 50.0; // 충분한 데이터가 없으면 중립값 반환
        }
        
        double avgGain = 0;
        double avgLoss = 0;
        
        // 첫 번째 평균 이득/손실 계산
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        // 나머지 기간에 대한 평균 이득/손실 계산
        for (int i = period + 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }
        
        // RSI 계산
        if (avgLoss == 0) {
            return 100.0;
        }
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    /**
     * RSI 신호 강도 계산
     */
    private double calculateRSISignalStrength(double rsi, int oversold, int overbought) {
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
        
        return Math.max(0, Math.min(100, signalStrength));
    }
    
    /**
     * 볼린저 밴드 신호 결정
     */
    private String determineBollingerSignal(double currentPrice, double upperBand, double middleBand, double lowerBand) {
        if (currentPrice >= upperBand) {
            return "UPPER_TOUCH"; // 상단 밴드 터치 (과매수 가능성)
        } else if (currentPrice <= lowerBand) {
            return "LOWER_TOUCH"; // 하단 밴드 터치 (과매도 가능성)
        } else if (currentPrice > middleBand) {
            return "UPPER_HALF"; // 중간~상단 밴드 사이
        } else if (currentPrice < middleBand) {
            return "LOWER_HALF"; // 중간~하단 밴드 사이
        } else {
            return "MIDDLE_CROSS"; // 중간 밴드 교차
        }
    }
    
    /**
     * 볼린저 밴드 신호 강도 계산
     */
    private double calculateBollingerSignalStrength(String signal, double currentPrice, 
                                                  double upperBand, double middleBand, 
                                                  double lowerBand, double bandWidth) {
        double signalStrength;
        
        if ("LOWER_TOUCH".equals(signal)) {
            // 하단 밴드 터치는 강한 매수 신호
            signalStrength = 80.0 + (bandWidth / 5.0); // 80% ~ 100%
        } else if ("LOWER_HALF".equals(signal)) {
            // 하단 절반은 중간 매수 신호
            double position = (currentPrice - lowerBand) / ((middleBand - lowerBand) / 2);
            signalStrength = 70.0 - (position * 10.0); // 60% ~ 70%
        } else if ("MIDDLE_CROSS".equals(signal)) {
            // 중간 밴드 교차는 중립 신호
            signalStrength = 50.0;
        } else if ("UPPER_HALF".equals(signal)) {
            // 상단 절반은 중간 매도 신호
            double position = (currentPrice - middleBand) / ((upperBand - middleBand) / 2);
            signalStrength = 40.0 - (position * 10.0); // 30% ~ 40%
        } else { // UPPER_TOUCH
            // 상단 밴드 터치는 강한 매도 신호
            signalStrength = 20.0 - (bandWidth / 5.0); // 0% ~ 20%
        }
        
        return Math.max(0, Math.min(100, signalStrength));
    }
    
    /**
     * 거래량 신호 강도 계산
     */
    private double calculateVolumeSignalStrength(double volumeChangePercent) {
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
            signalStrength = 50.0 + volumeChangePercent;
        } else {
            // 거래량이 20% 이상 감소하면 강한 부정 신호
            signalStrength = 30.0;
        }
        
        return Math.max(0, Math.min(100, signalStrength));
    }
} 