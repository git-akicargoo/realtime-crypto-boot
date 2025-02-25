package com.example.boot.exchange.layer6_analysis.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer1_core.config.ExchangeConfig;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@CrossOrigin  // CORS 허용
public class ExchangeConfigController {
    private final ExchangeConfig exchangeConfig;
    private static final Logger log = LoggerFactory.getLogger(ExchangeConfigController.class);

    @GetMapping("/supported-pairs")
    public ResponseEntity<Map<String, Object>> getSupportedPairs() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // 거래소별 지원 통화
            Map<String, List<String>> exchangeCurrencies = new HashMap<>();
            
            // null 체크 추가
            var exchanges = exchangeConfig.getExchanges();
            if (exchanges != null) {
                if (exchanges.getBinance() != null && exchanges.getBinance().getSupportedCurrencies() != null) {
                    exchangeCurrencies.put("BINANCE", exchanges.getBinance().getSupportedCurrencies());
                }
                if (exchanges.getUpbit() != null && exchanges.getUpbit().getSupportedCurrencies() != null) {
                    exchangeCurrencies.put("UPBIT", exchanges.getUpbit().getSupportedCurrencies());
                }
                if (exchanges.getBithumb() != null && exchanges.getBithumb().getSupportedCurrencies() != null) {
                    exchangeCurrencies.put("BITHUMB", exchanges.getBithumb().getSupportedCurrencies());
                }
            }

            // 공통 지원 심볼
            response.put("exchanges", exchangeCurrencies);
            response.put("symbols", exchangeConfig.getCommon().getSupportedSymbols());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get supported pairs", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 