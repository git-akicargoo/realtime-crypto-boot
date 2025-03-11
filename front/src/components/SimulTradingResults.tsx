import React, { useState, useEffect } from 'react';
import '../styles/SimulTradingResults.css';

interface SimulTradingResult {
  sessionId: string;
  cardId: string;
  exchange: string;
  symbol: string;
  quoteCurrency: string;
  currencyPair: string;
  initialBalance: number;
  finalBalance: number;
  profitPercent: number;
  signalThreshold: number;
  takeProfitPercent: number;
  stopLossPercent: number;
  totalTrades: number;
  winTrades: number;
  lossTrades: number;
  winRate: number;
  averageProfitPercent: number;
  maxProfitPercent: number;
  maxLossPercent: number;
  startTime: string;
  endTime: string;
  createdAt: string;
}

interface SimulTradingStats {
  cardId: string;
  totalCount: number;
  averageProfitPercent: number;
  averageWinRate: number;
}

interface SimulTradingResultsProps {
  cardId: string;
}

const SimulTradingResults: React.FC<SimulTradingResultsProps> = ({ cardId }) => {
  const [results, setResults] = useState<SimulTradingResult[]>([]);
  const [stats, setStats] = useState<SimulTradingStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 모의거래 결과 목록 조회
  useEffect(() => {
    if (!cardId) return;

    const fetchResults = async () => {
      try {
        setLoading(true);
        setError(null);

        const response = await fetch(`/api/simul-trading/results/${cardId}`);
        
        if (!response.ok) {
          throw new Error(`API 요청 실패: ${response.status}`);
        }
        
        const data = await response.json();
        setResults(data);
      } catch (err) {
        console.error('모의거래 결과 조회 중 오류 발생:', err);
        setError('모의거래 결과를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchResults();
  }, [cardId]);

  // 모의거래 통계 조회
  useEffect(() => {
    if (!cardId) return;

    const fetchStats = async () => {
      try {
        const response = await fetch(`/api/simul-trading/stats/${cardId}`);
        
        if (!response.ok) {
          throw new Error(`API 요청 실패: ${response.status}`);
        }
        
        const data = await response.json();
        setStats(data);
      } catch (err) {
        console.error('모의거래 통계 조회 중 오류 발생:', err);
        // 통계 조회 실패는 별도 에러 메시지 표시하지 않음
      }
    };

    fetchStats();
  }, [cardId]);

  // 날짜 포맷팅 함수
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString();
  };

  // 통계 정보 렌더링
  const renderStats = () => {
    if (!stats) return null;

    return (
      <div className="simul-stats">
        <h3>모의거래 통계</h3>
        <div className="stats-grid">
          <div className="stats-item">
            <span className="stats-label">총 시뮬레이션 횟수</span>
            <span className="stats-value">{stats.totalCount}회</span>
          </div>
          <div className="stats-item">
            <span className="stats-label">평균 수익률</span>
            <span className={`stats-value ${stats.averageProfitPercent >= 0 ? 'profit-positive' : 'profit-negative'}`}>
              {stats.averageProfitPercent.toFixed(2)}%
            </span>
          </div>
          <div className="stats-item">
            <span className="stats-label">평균 승률</span>
            <span className="stats-value">{stats.averageWinRate.toFixed(2)}%</span>
          </div>
        </div>
      </div>
    );
  };

  // 결과 목록 렌더링
  const renderResults = () => {
    if (loading) {
      return <div className="loading">데이터를 불러오는 중...</div>;
    }

    if (error) {
      return <div className="error-message">{error}</div>;
    }

    if (results.length === 0) {
      return <div className="no-results">모의거래 결과가 없습니다.</div>;
    }

    return (
      <div className="results-table-container">
        <table className="results-table">
          <thead>
            <tr>
              <th>시작 시간</th>
              <th>종료 시간</th>
              <th>초기 잔액</th>
              <th>최종 잔액</th>
              <th>수익률</th>
              <th>거래 횟수</th>
              <th>승률</th>
              <th>평균 수익률</th>
              <th>매수 신호 기준</th>
              <th>익절/손절</th>
            </tr>
          </thead>
          <tbody>
            {results.map((result) => (
              <tr key={result.sessionId}>
                <td>{formatDate(result.startTime)}</td>
                <td>{formatDate(result.endTime)}</td>
                <td>{result.initialBalance.toLocaleString()} 원</td>
                <td>{result.finalBalance.toLocaleString()} 원</td>
                <td className={result.profitPercent >= 0 ? 'profit-positive' : 'profit-negative'}>
                  {result.profitPercent.toFixed(2)}%
                </td>
                <td>{result.totalTrades}회 ({result.winTrades}승 {result.lossTrades}패)</td>
                <td>{result.winRate.toFixed(2)}%</td>
                <td className={result.averageProfitPercent >= 0 ? 'profit-positive' : 'profit-negative'}>
                  {result.averageProfitPercent.toFixed(2)}%
                </td>
                <td>{result.signalThreshold.toFixed(2)}</td>
                <td>{result.takeProfitPercent.toFixed(1)}% / {result.stopLossPercent.toFixed(1)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="simul-results-container">
      <h2>모의거래 결과</h2>
      {renderStats()}
      {renderResults()}
    </div>
  );
};

export default SimulTradingResults; 