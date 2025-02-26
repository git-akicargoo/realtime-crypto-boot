package com.example.boot.exchange.layer6_analysis.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.boot.exchange.layer6_analysis.dto.TradeStats;
import com.example.boot.exchange.layer6_analysis.model.MarketAnalysis;

@Repository
public interface AnalysisRepository extends JpaRepository<MarketAnalysis, Long> {
    
    @Query("SELECT a FROM MarketAnalysis a WHERE a.exchange = :exchange " +
           "AND a.currencyPair = :currencyPair AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<MarketAnalysis> findRecentAnalysis(String exchange, String currencyPair, LocalDateTime since);
    
    // 특정 거래쌍의 거래 통계 조회
    @Query("SELECT new com.example.boot.exchange.layer6_analysis.dto.TradeStats(" +
           "COUNT(a), " +
           "COUNT(CASE WHEN a.profitLossPercent > 0 THEN 1 END), " +
           "AVG(a.profitLossPercent), " +
           "MAX(a.profitLossPercent), " +
           "MIN(a.profitLossPercent)) " +
           "FROM MarketAnalysis a " +
           "WHERE a.exchange = :exchange AND a.currencyPair = :currencyPair " +
           "AND a.isCompleted = true")
    TradeStats getTradeStats(String exchange, String currencyPair);
    
    // 수익 거래 수 조회
    @Query("SELECT COUNT(a) FROM MarketAnalysis a WHERE a.isCompleted = true AND a.profitLossPercent > 0")
    long countProfitableTrades();
    
    // 전체 완료된 거래 수 조회
    @Query("SELECT COUNT(a) FROM MarketAnalysis a WHERE a.isCompleted = true")
    long countCompletedTrades();
    
    // 평균 수익률 조회
    @Query("SELECT AVG(a.profitLossPercent) FROM MarketAnalysis a WHERE a.isCompleted = true")
    Double getAverageProfitLoss();
} 