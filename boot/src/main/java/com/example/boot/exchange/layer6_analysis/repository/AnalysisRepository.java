package com.example.boot.exchange.layer6_analysis.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.boot.exchange.layer6_analysis.model.MarketAnalysis;

@Repository
public interface AnalysisRepository extends JpaRepository<MarketAnalysis, Long> {
    
    @Query("SELECT a FROM MarketAnalysis a WHERE a.exchange = :exchange " +
           "AND a.currencyPair = :currencyPair AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<MarketAnalysis> findRecentAnalysis(String exchange, String currencyPair, LocalDateTime since);
} 