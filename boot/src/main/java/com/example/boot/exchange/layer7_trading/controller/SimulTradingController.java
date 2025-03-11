package com.example.boot.exchange.layer7_trading.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisResponse;
import com.example.boot.exchange.layer6_analysis.dto.CardInfoDTO;
import com.example.boot.exchange.layer6_analysis.service.AnalysisDataSharingService;
import com.example.boot.exchange.layer7_trading.dto.SimulTradingRequest;
import com.example.boot.exchange.layer7_trading.dto.SimulTradingResponse;
import com.example.boot.exchange.layer7_trading.entity.SimulTradingResult;
import com.example.boot.exchange.layer7_trading.repository.SimulTradingResultRepository;
import com.example.boot.exchange.layer7_trading.service.SimulTradingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의거래 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulTradingController {
    
    private final AnalysisDataSharingService dataSharingService;
    private final SimulTradingService simulTradingService;
    private final SimulTradingResultRepository simulTradingResultRepository;
    
    /**
     * 모든 분석 카드 목록 조회 API
     * @return 카드 목록
     */
    @GetMapping("/cards")
    public ResponseEntity<List<CardInfoDTO>> getAllCards() {
        log.info("모든 분석 카드 목록 조회 요청 수신");
        List<CardInfoDTO> cards = dataSharingService.getAllCardInfoList();
        log.info("조회된 카드 수: {}", cards.size());
        return ResponseEntity.ok(cards);
    }
    
    /**
     * 특정 카드의 최신 분석 결과 조회 API
     * @param cardId 카드 ID
     * @return 최신 분석 결과
     */
    @GetMapping("/cards/{cardId}/latest")
    public ResponseEntity<?> getLatestAnalysisResult(@PathVariable String cardId) {
        log.info("카드 ID: {}의 최신 분석 결과 조회 요청 수신", cardId);
        
        AnalysisResponse response = dataSharingService.getLatestAnalysisResult(cardId);
        if (response == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "해당 카드의 분석 결과가 없습니다.");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 모의거래 시작 API
     * @param request 모의거래 요청 정보
     * @return 모의거래 응답 정보
     */
    @PostMapping("/trading/start")
    public ResponseEntity<?> startSimulTrading(@RequestBody SimulTradingRequest request) {
        log.info("모의거래 시작 요청 수신: {}", request);
        
        try {
            // 요청 시간 설정
            request.setTimestamp(System.currentTimeMillis());
            
            // 모의거래 시작
            SimulTradingResponse response = simulTradingService.startSimulTrading(request);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모의거래 시작 중 오류 발생", e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 모의거래 중지 API
     * @param sessionId 모의거래 세션 ID
     * @return 모의거래 응답 정보
     */
    @DeleteMapping("/trading/{sessionId}")
    public ResponseEntity<?> stopSimulTrading(@PathVariable String sessionId) {
        log.info("모의거래 중지 요청 수신: {}", sessionId);
        
        try {
            // 모의거래 중지
            SimulTradingResponse response = simulTradingService.stopSimulTrading(sessionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모의거래 중지 중 오류 발생", e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 모의거래 상태 조회 API
     * @param sessionId 모의거래 세션 ID
     * @return 모의거래 응답 정보
     */
    @GetMapping("/trading/{sessionId}")
    public ResponseEntity<?> getSimulTradingStatus(@PathVariable String sessionId) {
        log.info("모의거래 상태 조회 요청 수신: {}", sessionId);
        
        try {
            // 모의거래 상태 조회
            SimulTradingResponse response = simulTradingService.getSimulTradingStatus(sessionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("모의거래 상태 조회 중 오류 발생", e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 모의거래 테스트 API (기존 코드 유지)
     */
    @PostMapping("/trade/test")
    public ResponseEntity<Map<String, Object>> testTradeRequest(@RequestBody Object request) {
        log.info("모의 거래 테스트 요청 수신: {}", request);
        
        // 응답 데이터 구성
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "모의 거래 테스트 요청이 성공적으로 처리되었습니다.");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("receivedData", request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 카드 ID로 모의거래 결과 목록 조회
     * @param cardId 카드 ID
     * @return 모의거래 결과 목록
     */
    @GetMapping("/results/{cardId}")
    public ResponseEntity<List<SimulTradingResult>> getSimulTradingResults(@PathVariable String cardId) {
        log.info("모의거래 결과 목록 조회: cardId={}", cardId);
        List<SimulTradingResult> results = simulTradingResultRepository.findByCardIdOrderByEndTimeDesc(cardId);
        return ResponseEntity.ok(results);
    }
    
    /**
     * 카드 ID로 최근 모의거래 결과 조회
     * @param cardId 카드 ID
     * @return 최근 모의거래 결과
     */
    @GetMapping("/results/latest/{cardId}")
    public ResponseEntity<SimulTradingResult> getLatestSimulTradingResult(@PathVariable String cardId) {
        log.info("최근 모의거래 결과 조회: cardId={}", cardId);
        SimulTradingResult result = simulTradingResultRepository.findFirstByCardIdOrderByEndTimeDesc(cardId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
    
    /**
     * 카드 ID로 모의거래 통계 조회
     * @param cardId 카드 ID
     * @return 모의거래 통계
     */
    @GetMapping("/stats/{cardId}")
    public ResponseEntity<SimulTradingStats> getSimulTradingStats(@PathVariable String cardId) {
        log.info("모의거래 통계 조회: cardId={}", cardId);
        
        long totalCount = simulTradingResultRepository.countByCardId(cardId);
        Double avgProfitPercent = simulTradingResultRepository.findAverageProfitPercentByCardId(cardId);
        Double avgWinRate = simulTradingResultRepository.findAverageWinRateByCardId(cardId);
        
        SimulTradingStats stats = SimulTradingStats.builder()
                .cardId(cardId)
                .totalCount(totalCount)
                .averageProfitPercent(avgProfitPercent != null ? avgProfitPercent : 0.0)
                .averageWinRate(avgWinRate != null ? avgWinRate : 0.0)
                .build();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 모의거래 통계 DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class SimulTradingStats {
        private String cardId;
        private long totalCount;
        private double averageProfitPercent;
        private double averageWinRate;
    }
} 