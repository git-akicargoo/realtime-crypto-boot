import React, { useState, useEffect, useRef } from 'react';
import '../styles/SimulTrading.css';

interface TradeHistory {
  tradeId: string;
  type: string;
  price: number;
  amount: number;
  total: number;
  profitPercent?: number;
  reason?: string;
  tradeTime: string;
}

interface SimulTradingResponse {
  sessionId: string;
  cardId: string;
  exchange: string;
  symbol: string;
  quoteCurrency: string;
  currencyPair: string;
  initialBalance: number;
  currentBalance: number;
  profitPercent: number;
  signalThreshold: number;
  takeProfitPercent: number;
  stopLossPercent: number;
  status: string;
  holdingPosition: boolean;
  entryPrice?: number;
  entryTime?: string;
  totalTrades: number;
  winTrades: number;
  lossTrades: number;
  startTime: string;
  lastUpdateTime: string;
  recentTrades: TradeHistory[];
  completedTrades: number;
  winRate: number;
  averageProfitPercent: number;
  maxProfitPercent: number;
  maxLossPercent: number;
  completed: boolean;
  message?: string;
}

interface SimulTradingProps {
  cardId: string;
}

const SimulTrading: React.FC<SimulTradingProps> = ({ cardId }) => {
  const [connected, setConnected] = useState<boolean>(false);
  const [tradingStarted, setTradingStarted] = useState<boolean>(false);
  const [tradingData, setTradingData] = useState<SimulTradingResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [initialBalance, setInitialBalance] = useState<number>(1000000);
  const [signalThreshold, setSignalThreshold] = useState<number>(0.7);
  const [takeProfitPercent, setTakeProfitPercent] = useState<number>(3.0);
  const [stopLossPercent, setStopLossPercent] = useState<number>(2.0);
  
  const socketRef = useRef<WebSocket | null>(null);

  // WebSocket 연결 설정
  useEffect(() => {
    if (!cardId) return;

    // WebSocket 연결
    const connectWebSocket = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/api/ws/simul-trading`;
      
      const socket = new WebSocket(wsUrl);
      socketRef.current = socket;

      socket.onopen = () => {
        console.log('모의거래 WebSocket 연결됨');
        setConnected(true);
        setError(null);
      };

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          
          // 에러 메시지 처리
          if (data.error) {
            setError(data.error);
            return;
          }
          
          // 모의거래 데이터 업데이트
          setTradingData(data);
          
          // 모의거래 시작 상태 업데이트
          if (data.status === 'RUNNING' || data.status === 'COMPLETED') {
            setTradingStarted(true);
          } else {
            setTradingStarted(false);
          }
          
          // 완료 메시지 처리
          if (data.completed && data.message) {
            alert(data.message);
          }
        } catch (err) {
          console.error('WebSocket 메시지 처리 중 오류 발생:', err);
        }
      };

      socket.onclose = () => {
        console.log('모의거래 WebSocket 연결 종료');
        setConnected(false);
        
        // 자동 재연결 (5초 후)
        setTimeout(() => {
          if (socketRef.current === socket) {
            connectWebSocket();
          }
        }, 5000);
      };

      socket.onerror = (err) => {
        console.error('모의거래 WebSocket 오류:', err);
        setError('WebSocket 연결 오류가 발생했습니다.');
      };
    };

    connectWebSocket();

    // 컴포넌트 언마운트 시 WebSocket 연결 종료
    return () => {
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
    };
  }, [cardId]);

  // 모의거래 시작
  const startSimulTrading = () => {
    if (!socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) {
      setError('WebSocket 연결이 없습니다.');
      return;
    }

    const request = {
      action: 'startSimulTrading',
      cardId: cardId,
      initialBalance: initialBalance,
      signalThreshold: signalThreshold,
      takeProfitPercent: takeProfitPercent,
      stopLossPercent: stopLossPercent
    };

    socketRef.current.send(JSON.stringify(request));
  };

  // 모의거래 중지
  const stopSimulTrading = () => {
    if (!socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) {
      setError('WebSocket 연결이 없습니다.');
      return;
    }

    const request = {
      action: 'stopSimulTrading'
    };

    socketRef.current.send(JSON.stringify(request));
  };

  // 거래 내역 테이블 렌더링
  const renderTradeHistory = () => {
    if (!tradingData || !tradingData.recentTrades || tradingData.recentTrades.length === 0) {
      return <p>거래 내역이 없습니다.</p>;
    }

    return (
      <table className="trade-history-table">
        <thead>
          <tr>
            <th>유형</th>
            <th>가격</th>
            <th>수량</th>
            <th>총액</th>
            <th>수익률</th>
            <th>이유</th>
            <th>시간</th>
          </tr>
        </thead>
        <tbody>
          {tradingData.recentTrades.map((trade) => (
            <tr key={trade.tradeId} className={trade.type === 'BUY' ? 'buy-row' : 'sell-row'}>
              <td>{trade.type === 'BUY' ? '매수' : '매도'}</td>
              <td>{trade.price.toLocaleString()}</td>
              <td>{trade.amount.toFixed(8)}</td>
              <td>{trade.total.toLocaleString()}</td>
              <td>
                {trade.profitPercent !== undefined 
                  ? <span className={trade.profitPercent >= 0 ? 'profit-positive' : 'profit-negative'}>
                      {trade.profitPercent.toFixed(2)}%
                    </span>
                  : '-'}
              </td>
              <td>{trade.reason || '-'}</td>
              <td>{new Date(trade.tradeTime).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  // 모의거래 요약 정보 렌더링
  const renderTradingSummary = () => {
    if (!tradingData) return null;

    return (
      <div className="trading-summary">
        <div className="summary-item">
          <span className="summary-label">초기 잔액</span>
          <span className="summary-value">{tradingData.initialBalance.toLocaleString()} 원</span>
        </div>
        <div className="summary-item">
          <span className="summary-label">현재 잔액</span>
          <span className="summary-value">{tradingData.currentBalance.toLocaleString()} 원</span>
        </div>
        <div className="summary-item">
          <span className="summary-label">수익률</span>
          <span className={`summary-value ${tradingData.profitPercent >= 0 ? 'profit-positive' : 'profit-negative'}`}>
            {tradingData.profitPercent.toFixed(2)}%
          </span>
        </div>
        <div className="summary-item">
          <span className="summary-label">거래 횟수</span>
          <span className="summary-value">{tradingData.totalTrades} / {tradingData.completedTrades}</span>
        </div>
        <div className="summary-item">
          <span className="summary-label">승률</span>
          <span className="summary-value">{tradingData.winRate.toFixed(2)}%</span>
        </div>
        <div className="summary-item">
          <span className="summary-label">평균 수익률</span>
          <span className={`summary-value ${tradingData.averageProfitPercent >= 0 ? 'profit-positive' : 'profit-negative'}`}>
            {tradingData.averageProfitPercent.toFixed(2)}%
          </span>
        </div>
      </div>
    );
  };

  return (
    <div className="simul-trading-container">
      <h2>모의거래 시뮬레이션</h2>
      
      {error && <div className="error-message">{error}</div>}
      
      <div className="connection-status">
        <span className={`status-indicator ${connected ? 'connected' : 'disconnected'}`}></span>
        <span>{connected ? '연결됨' : '연결 중...'}</span>
      </div>
      
      {!tradingStarted ? (
        <div className="trading-form">
          <div className="form-group">
            <label>초기 잔액 (원)</label>
            <input 
              type="number" 
              value={initialBalance} 
              onChange={(e) => setInitialBalance(Number(e.target.value))}
              min="100000"
              step="100000"
            />
          </div>
          
          <div className="form-group">
            <label>매수 신호 기준값 (0.0 ~ 1.0)</label>
            <input 
              type="number" 
              value={signalThreshold} 
              onChange={(e) => setSignalThreshold(Number(e.target.value))}
              min="0"
              max="1"
              step="0.1"
            />
          </div>
          
          <div className="form-group">
            <label>익절 기준 (%)</label>
            <input 
              type="number" 
              value={takeProfitPercent} 
              onChange={(e) => setTakeProfitPercent(Number(e.target.value))}
              min="0.5"
              step="0.5"
            />
          </div>
          
          <div className="form-group">
            <label>손절 기준 (%)</label>
            <input 
              type="number" 
              value={stopLossPercent} 
              onChange={(e) => setStopLossPercent(Number(e.target.value))}
              min="0.5"
              step="0.5"
            />
          </div>
          
          <button 
            className="start-button" 
            onClick={startSimulTrading}
            disabled={!connected || !cardId}
          >
            모의거래 시작
          </button>
        </div>
      ) : (
        <div className="trading-dashboard">
          {renderTradingSummary()}
          
          <div className="position-info">
            <h3>포지션 정보</h3>
            {tradingData?.holdingPosition ? (
              <div className="position-details">
                <div className="position-item">
                  <span className="position-label">진입 가격</span>
                  <span className="position-value">{tradingData.entryPrice?.toLocaleString()}</span>
                </div>
                <div className="position-item">
                  <span className="position-label">진입 시간</span>
                  <span className="position-value">{new Date(tradingData.entryTime || '').toLocaleString()}</span>
                </div>
              </div>
            ) : (
              <p>현재 보유 중인 포지션이 없습니다.</p>
            )}
          </div>
          
          <div className="trade-history">
            <h3>거래 내역</h3>
            {renderTradeHistory()}
          </div>
          
          <button 
            className="stop-button" 
            onClick={stopSimulTrading}
            disabled={!connected || tradingData?.status === 'COMPLETED'}
          >
            모의거래 중지
          </button>
        </div>
      )}
    </div>
  );
};

export default SimulTrading; 