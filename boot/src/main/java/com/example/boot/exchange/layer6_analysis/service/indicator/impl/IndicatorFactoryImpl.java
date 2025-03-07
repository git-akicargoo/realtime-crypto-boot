package com.example.boot.exchange.layer6_analysis.service.indicator.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer6_analysis.service.indicator.BollingerBandsIndicator;
import com.example.boot.exchange.layer6_analysis.service.indicator.IndicatorFactory;
import com.example.boot.exchange.layer6_analysis.service.indicator.RSIIndicator;
import com.example.boot.exchange.layer6_analysis.service.indicator.SMAIndicator;
import com.example.boot.exchange.layer6_analysis.service.indicator.VolumeIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * 인디케이터 팩토리 구현체
 * 트레이딩 스타일에 따라 적절한 인디케이터 인스턴스를 제공합니다.
 */
@Slf4j
@Component
public class IndicatorFactoryImpl implements IndicatorFactory {

    private final SMAIndicator smaIndicator;
    private final RSIIndicator rsiIndicator;
    private final BollingerBandsIndicator bollingerBandsIndicator;
    private final VolumeIndicator volumeIndicator;
    
    @Autowired
    public IndicatorFactoryImpl(
            SMAIndicator smaIndicator,
            RSIIndicator rsiIndicator,
            BollingerBandsIndicator bollingerBandsIndicator,
            VolumeIndicator volumeIndicator) {
        this.smaIndicator = smaIndicator;
        this.rsiIndicator = rsiIndicator;
        this.bollingerBandsIndicator = bollingerBandsIndicator;
        this.volumeIndicator = volumeIndicator;
    }
    
    @Override
    public SMAIndicator getSMAIndicator(String tradingStyle) {
        synchronized (smaIndicator) {
            // 트레이딩 스타일에 맞는 파라미터 설정
            smaIndicator.setTradingStyleParameters(tradingStyle);
            log.debug("SMA 인디케이터 생성: tradingStyle={}", tradingStyle);
            return smaIndicator;
        }
    }
    
    @Override
    public RSIIndicator getRSIIndicator(String tradingStyle) {
        synchronized (rsiIndicator) {
            // 트레이딩 스타일에 맞는 파라미터 설정
            rsiIndicator.setTradingStyleParameters(tradingStyle);
            log.debug("RSI 인디케이터 생성: tradingStyle={}", tradingStyle);
            return rsiIndicator;
        }
    }
    
    @Override
    public BollingerBandsIndicator getBollingerBandsIndicator(String tradingStyle) {
        synchronized (bollingerBandsIndicator) {
            // 트레이딩 스타일에 맞는 파라미터 설정
            bollingerBandsIndicator.setTradingStyleParameters(tradingStyle);
            log.debug("볼린저 밴드 인디케이터 생성: tradingStyle={}", tradingStyle);
            return bollingerBandsIndicator;
        }
    }
    
    @Override
    public VolumeIndicator getVolumeIndicator(String tradingStyle) {
        synchronized (volumeIndicator) {
            // 트레이딩 스타일에 맞는 파라미터 설정
            volumeIndicator.setTradingStyleParameters(tradingStyle);
            log.debug("거래량 인디케이터 생성: tradingStyle={}", tradingStyle);
            return volumeIndicator;
        }
    }
} 