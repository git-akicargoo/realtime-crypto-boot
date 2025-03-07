package com.example.boot.exchange.layer6_analysis.service.indicator;

/**
 * 트레이딩 스타일에 따라 적절한 인디케이터를 제공하는 팩토리 인터페이스
 */
public interface IndicatorFactory {
    
    /**
     * SMA 인디케이터를 반환합니다.
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     * @return SMA 인디케이터
     */
    SMAIndicator getSMAIndicator(String tradingStyle);
    
    /**
     * RSI 인디케이터를 반환합니다.
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     * @return RSI 인디케이터
     */
    RSIIndicator getRSIIndicator(String tradingStyle);
    
    /**
     * 볼린저 밴드 인디케이터를 반환합니다.
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     * @return 볼린저 밴드 인디케이터
     */
    BollingerBandsIndicator getBollingerBandsIndicator(String tradingStyle);
    
    /**
     * 거래량 인디케이터를 반환합니다.
     * 
     * @param tradingStyle 트레이딩 스타일 (SCALPING, DAY_TRADING, SWING)
     * @return 거래량 인디케이터
     */
    VolumeIndicator getVolumeIndicator(String tradingStyle);
} 