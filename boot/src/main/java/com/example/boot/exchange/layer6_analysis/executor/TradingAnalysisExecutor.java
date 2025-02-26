package com.example.boot.exchange.layer6_analysis.executor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.service.MarketAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingAnalysisExecutor {
    private final MarketAnalysisService analysisService;
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    
    public AnalysisResponse executeAnalysis(StandardExchangeData data, 
                                          double priceDropThreshold, 
                                          double volumeIncreaseThreshold,
                                          int smaShortPeriod,
                                          int smaLongPeriod) {
        try {
            return analysisService.analyzeRebound(data, 
                                                priceDropThreshold, 
                                                volumeIncreaseThreshold,
                                                smaShortPeriod,
                                                smaLongPeriod);
        } catch (Exception e) {
            log.error("Analysis execution failed: {}", e.getMessage());
            throw new RuntimeException("Analysis execution failed", e);
        }
    }

    public void stopAnalysis(String exchange, String currencyPair) {
        String key = getSubscriptionKey(exchange, currencyPair);
        Disposable subscription = activeSubscriptions.remove(key);
        
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Stopped analysis for {}-{}", exchange, currencyPair);
        }
    }

    public void registerSubscription(String exchange, String currencyPair, Disposable subscription) {
        String key = getSubscriptionKey(exchange, currencyPair);
        Disposable oldSubscription = activeSubscriptions.put(key, subscription);
        
        if (oldSubscription != null && !oldSubscription.isDisposed()) {
            oldSubscription.dispose();
        }
    }

    private String getSubscriptionKey(String exchange, String currencyPair) {
        return exchange + "-" + currencyPair;
    }
} 