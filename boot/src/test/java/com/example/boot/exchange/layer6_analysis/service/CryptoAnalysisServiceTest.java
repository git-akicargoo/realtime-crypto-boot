package com.example.boot.exchange.layer6_analysis.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.boot.exchange.layer1_core.model.CurrencyPair;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer5_price_cache.redis.service.RedisCacheService;
import com.example.boot.exchange.layer6_analysis.config.TradingStyleConfig;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class CryptoAnalysisServiceTest {

    @Mock
    private RedisCacheService cacheService;
    
    @Mock
    private IndicatorCalculationService indicatorService;
    
    @Mock
    private AnalysisResponseConverter responseConverter;
    
    @Mock
    private TradingStyleConfig tradingStyleConfig;
    
    @InjectMocks
    private CryptoAnalysisService cryptoAnalysisService;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    public void testAnalyzeMarketData() {
        // 테스트 데이터 준비
        StandardExchangeData currentData = createTestMarketData("BINANCE", "USDT-BTC", 50000.0, 1.5);
        List<StandardExchangeData> history = createTestHistory();
        AnalysisRequest request = createTestRequest();
        
        // Mock 설정
        when(indicatorService.calculateSMA(any(), any())).thenReturn(Map.of(
            "shortDiff", 1.5,
            "mediumDiff", 0.8,
            "longDiff", -0.5,
            "breakout", true,
            "smaSignal", "BULLISH",
            "smaSignalStrength", 65.0
        ));
        
        when(indicatorService.calculateRSI(any(), any())).thenReturn(Map.of(
            "rsiValue", 45.0,
            "rsiSignal", "NEUTRAL",
            "rsiSignalStrength", 50.0
        ));
        
        when(indicatorService.calculateBollingerBands(any(), any())).thenReturn(Map.of(
            "bollingerUpper", 52000.0,
            "bollingerMiddle", 50000.0,
            "bollingerLower", 48000.0,
            "bollingerWidth", 8.0,
            "bollingerSignal", "MIDDLE_CROSS",
            "bbSignalStrength", 50.0
        ));
        
        when(indicatorService.analyzeVolume(any())).thenReturn(Map.of(
            "volumeChangePercent", 20.0,
            "volumeSignalStrength", 70.0
        ));
        
        AnalysisResponse expectedResponse = AnalysisResponse.builder()
            .exchange("BINANCE")
            .currencyPair("USDT-BTC")
            .symbol("BTC")
            .quoteCurrency("USDT")
            .currentPrice(50000.0)
            .analysisResult("BUY")
            .build();
        
        when(responseConverter.convertToAnalysisResponse(
            any(), any(), any(), any(Double.class), any(Double.class), 
            any(Double.class), any(Double.class), any())).thenReturn(expectedResponse);
        
        // 실행
        AnalysisResponse result = cryptoAnalysisService.analyzeMarketData(currentData, history, request);
        
        // 검증: null이 아니고 예상된 값들을 포함하는지 확인
        assert result != null;
        assert "BINANCE".equals(result.getExchange());
        assert "USDT-BTC".equals(result.getCurrencyPair());
        assert "BUY".equals(result.getAnalysisResult());
    }
    
    @Test
    public void testStartAnalysis() {
        // 테스트 데이터 준비
        AnalysisRequest request = createTestRequest();
        StandardExchangeData marketData = createTestMarketData("BINANCE", "USDT-BTC", 50000.0, 1.5);
        List<StandardExchangeData> history = createTestHistory();
        
        // Mock 설정
        when(cacheService.subscribeToMarketData(anyString(), anyString()))
            .thenReturn(Flux.just(marketData));
        
        when(cacheService.getAnalysisWindow(anyString(), anyString()))
            .thenReturn(history);
        
        AnalysisResponse expectedResponse = AnalysisResponse.builder()
            .exchange("BINANCE")
            .currencyPair("USDT-BTC")
            .symbol("BTC")
            .quoteCurrency("USDT")
            .currentPrice(50000.0)
            .analysisResult("BUY")
            .build();
        
        when(responseConverter.convertToAnalysisResponse(
            any(), any(), any(), any(Double.class), any(Double.class), 
            any(Double.class), any(Double.class), any())).thenReturn(expectedResponse);
        
        // 실행 및 검증
        StepVerifier.create(cryptoAnalysisService.startAnalysis(request).take(2))
            .expectNextMatches(response -> 
                response != null && 
                "WAITING_FOR_DATA".equals(response.getAnalysisResult()))
            .expectNextMatches(response -> 
                response != null && 
                "BINANCE".equals(response.getExchange()) &&
                "USDT-BTC".equals(response.getCurrencyPair()))
            .verifyComplete();
    }
    
    // 테스트 데이터 생성 헬퍼 메서드
    private StandardExchangeData createTestMarketData(String exchange, String currencyPair, double price, double volume) {
        String[] parts = currencyPair.split("-");
        return StandardExchangeData.builder()
            .exchange(exchange)
            .currencyPair(new CurrencyPair(parts[0], parts[1]))
            .price(BigDecimal.valueOf(price))
            .volume(BigDecimal.valueOf(volume))
            .timestamp(Instant.now())
            .build();
    }
    
    private List<StandardExchangeData> createTestHistory() {
        List<StandardExchangeData> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // 약간의 가격 변동을 가진 히스토리 데이터 생성
            double price = 50000.0 + (Math.random() - 0.5) * 1000;
            double volume = 1.0 + Math.random();
            history.add(createTestMarketData("BINANCE", "USDT-BTC", price, volume));
        }
        return history;
    }
    
    private AnalysisRequest createTestRequest() {
        AnalysisRequest request = new AnalysisRequest();
        request.setExchange("BINANCE");
        request.setCurrencyPair("USDT-BTC");
        request.setSymbol("BTC");
        request.setQuoteCurrency("USDT");
        request.setTradingStyle("DAY_TRADING");
        request.setSmaShortPeriod(5);
        request.setSmaMediumPeriod(15);
        request.setSmaLongPeriod(30);
        request.setRsiPeriod(14);
        request.setRsiOverbought(70);
        request.setRsiOversold(30);
        request.setBollingerPeriod(20);
        request.setBollingerDeviation(2.0);
        return request;
    }
} 