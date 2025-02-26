package com.example.boot.exchange.layer6_analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TradeStats {
    private long totalTrades;           // 전체 거래 수
    private long profitableTrades;      // 수익 거래 수
    private Double averageProfitLoss;   // 평균 수익률
    private Double maxProfit;           // 최대 수익률
    private Double minProfit;           // 최소 수익률 (최대 손실률)
    
    public double getWinRate() {
        return totalTrades > 0 ? (double) profitableTrades / totalTrades * 100 : 0.0;
    }
    
    public String getFormattedStats() {
        return String.format(
            "Total Trades: %d, Win Rate: %.2f%%, Avg PnL: %.2f%%, Max Profit: %.2f%%, Max Loss: %.2f%%",
            totalTrades,
            getWinRate(),
            averageProfitLoss != null ? averageProfitLoss : 0.0,
            maxProfit != null ? maxProfit : 0.0,
            minProfit != null ? minProfit : 0.0
        );
    }
} 