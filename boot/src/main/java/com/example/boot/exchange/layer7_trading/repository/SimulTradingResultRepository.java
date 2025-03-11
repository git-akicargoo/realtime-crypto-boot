package com.example.boot.exchange.layer7_trading.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.boot.exchange.layer7_trading.entity.SimulTradingResult;

/**
 * 모의거래 결과 리포지토리
 */
@Repository
public interface SimulTradingResultRepository extends JpaRepository<SimulTradingResult, String> {
    
    /**
     * 카드 ID로 모의거래 결과 목록 조회
     * @param cardId 카드 ID
     * @return 모의거래 결과 목록
     */
    List<SimulTradingResult> findByCardIdOrderByEndTimeDesc(String cardId);
    
    /**
     * 카드 ID로 최근 모의거래 결과 조회
     * @param cardId 카드 ID
     * @return 최근 모의거래 결과
     */
    SimulTradingResult findFirstByCardIdOrderByEndTimeDesc(String cardId);
    
    /**
     * 카드 ID로 모의거래 결과 개수 조회
     * @param cardId 카드 ID
     * @return 모의거래 결과 개수
     */
    long countByCardId(String cardId);
    
    /**
     * 카드 ID로 평균 수익률 조회
     * @param cardId 카드 ID
     * @return 평균 수익률
     */
    @Query("SELECT AVG(r.profitPercent) FROM SimulTradingResult r WHERE r.cardId = :cardId")
    Double findAverageProfitPercentByCardId(@Param("cardId") String cardId);
    
    /**
     * 카드 ID로 평균 승률 조회
     * @param cardId 카드 ID
     * @return 평균 승률
     */
    @Query("SELECT AVG(r.winRate) FROM SimulTradingResult r WHERE r.cardId = :cardId")
    Double findAverageWinRateByCardId(@Param("cardId") String cardId);
} 