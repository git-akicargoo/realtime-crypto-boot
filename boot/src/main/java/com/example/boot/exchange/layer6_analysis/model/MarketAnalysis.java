package com.example.boot.exchange.layer6_analysis.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_analysis")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String exchange;
    private String currencyPair;
    private LocalDateTime timestamp;
    
    private double currentPrice;
    private double priceChangePercent;
    private double volume;
    private double reboundProbability;
    
    private double volumeChangePercent;
    private double priceVolatility;
    private String analysisDetails;
} 