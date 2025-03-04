package com.example.boot.exchange.layer6_analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer6_analysis.dto.SimulationTradeRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationTradeController {
    
    @PostMapping("/trade/test")
    public ResponseEntity<Map<String, Object>> testTradeRequest(@RequestBody SimulationTradeRequest request) {
        log.info("모의 거래 테스트 요청 수신: {}", request);
        
        // 거래 설정 정보 로깅
        log.info("거래 설정: {}", request.getTradingSettings());
        
        // 시장 데이터 로깅
        log.info("시장 데이터: {}", request.getMarketData());
        
        // 응답 데이터 구성
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "모의 거래 테스트 요청이 성공적으로 처리되었습니다.");
        response.put("requestId", System.currentTimeMillis()); // 임시 요청 ID
        response.put("receivedData", request);
        
        return ResponseEntity.ok(response);
    }
} 