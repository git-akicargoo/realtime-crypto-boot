package com.example.boot.exchange.layer6_analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer6_analysis.dto.TradeStats;
import com.example.boot.exchange.layer6_analysis.repository.AnalysisRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/analysis/stats")
@RequiredArgsConstructor
public class AnalysisStatsController {
    private final AnalysisRepository analysisRepository;
    
    @GetMapping("/{exchange}/{currencyPair}")
    public ResponseEntity<TradeStats> getTradeStats(
            @PathVariable String exchange,
            @PathVariable String currencyPair) {
        
        TradeStats stats = analysisRepository.getTradeStats(exchange, currencyPair);
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getOverallStats() {
        long totalTrades = analysisRepository.countCompletedTrades();
        long profitableTrades = analysisRepository.countProfitableTrades();
        Double avgProfitLoss = analysisRepository.getAverageProfitLoss();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTrades", totalTrades);
        summary.put("profitableTrades", profitableTrades);
        summary.put("winRate", totalTrades > 0 ? (double)profitableTrades/totalTrades * 100 : 0);
        summary.put("averageProfitLoss", avgProfitLoss != null ? avgProfitLoss : 0);
        
        return ResponseEntity.ok(summary);
    }
} 