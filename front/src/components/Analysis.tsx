import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';

interface ExchangeConfig {
    exchanges: string[];
    pairs: {
        [exchange: string]: string[];
    };
}

interface AnalysisParams {
    priceDropThreshold: number;
    volumeIncreaseThreshold: number;
    smaShortPeriod: number;
    smaLongPeriod: number;
}

const Analysis: React.FC = () => {
    const [exchangeConfig, setExchangeConfig] = useState<ExchangeConfig | null>(null);
    const [selectedExchange, setSelectedExchange] = useState<string>('');
    const [availablePairs, setAvailablePairs] = useState<string[]>([]);
    const [selectedPair, setSelectedPair] = useState<string>('');
    const [analysisParams, setAnalysisParams] = useState<AnalysisParams>({
        priceDropThreshold: -1,
        volumeIncreaseThreshold: 50,
        smaShortPeriod: 1,
        smaLongPeriod: 3
    });
    const [analysisResults, setAnalysisResults] = useState<any[]>([]);
    const stompClientRef = useRef<Client | null>(null);

    // 거래소 설정 로드
    useEffect(() => {
        fetch('http://localhost:8080/api/v1/exchange/config')
            .then(res => res.json())
            .then((data: ExchangeConfig) => {
                setExchangeConfig(data);
            })
            .catch(err => console.error('Failed to load exchange config:', err));
    }, []);

    // 거래소 선택 시 해당 거래소의 페어 목록 업데이트
    useEffect(() => {
        if (exchangeConfig && selectedExchange) {
            setAvailablePairs(exchangeConfig.pairs[selectedExchange.toLowerCase()]);
        }
    }, [selectedExchange, exchangeConfig]);

    // STOMP 클라이언트 연결
    useEffect(() => {
        const stompClient = new Client({
            brokerURL: 'ws://localhost:8080/ws/stomp/analysis',
            debug: function (str) {
                console.log('STOMP: ' + str);
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000
        });

        stompClient.onConnect = () => {
            console.log('STOMP Connected');
            
            // 분석 결과 구독
            stompClient.subscribe('/topic/analysis', message => {
                try {
                    const data = JSON.parse(message.body);
                    setAnalysisResults(prev => [...prev, data]);
                } catch (e) {
                    console.error('Failed to parse STOMP message:', e);
                }
            });
            
            // 에러 메시지 구독
            stompClient.subscribe('/topic/analysis.error', message => {
                console.error('Analysis error:', message.body);
                alert('Analysis error: ' + message.body);
            });
            
            // 분석 중지 메시지 구독
            stompClient.subscribe('/topic/analysis.stop', message => {
                console.log('Analysis stopped:', message.body);
            });
        };
        
        stompClient.onStompError = (frame) => {
            console.error('STOMP error:', frame.headers['message']);
            console.error('Additional details:', frame.body);
        };
        
        stompClient.activate();
        stompClientRef.current = stompClient;
        
        return () => {
            if (stompClient.connected) {
                stompClient.deactivate();
            }
        };
    }, []);

    const handleParamChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setAnalysisParams(prev => ({
            ...prev,
            [name]: Number(value)
        }));
    };

    const startAnalysis = () => {
        if (!selectedExchange || !selectedPair) {
            alert('Please select exchange and currency pair');
            return;
        }
        
        const request = {
            action: 'startAnalysis',
            exchange: selectedExchange,
            currencyPair: selectedPair,
            ...analysisParams
        };
        
        if (stompClientRef.current && stompClientRef.current.connected) {
            console.log('Sending analysis request:', request);
            stompClientRef.current.publish({
                destination: '/app/analysis.start',
                body: JSON.stringify(request)
            });
        } else {
            console.error('STOMP not connected');
        }
    };
    
    const stopAnalysis = () => {
        if (!selectedExchange || !selectedPair) {
            return;
        }
        
        const request = {
            action: 'stopAnalysis',
            exchange: selectedExchange,
            currencyPair: selectedPair
        };
        
        if (stompClientRef.current && stompClientRef.current.connected) {
            console.log('Sending stop request:', request);
            stompClientRef.current.publish({
                destination: '/app/analysis.stop',
                body: JSON.stringify(request)
            });
        } else {
            console.error('STOMP not connected');
        }
    };

    return (
        <div className="analysis-container">
            <h2>Market Analysis</h2>
            
            <div className="analysis-form">
                <div className="form-group">
                    <label>Exchange:</label>
                    <select 
                        value={selectedExchange} 
                        onChange={e => setSelectedExchange(e.target.value)}
                    >
                        <option value="">Select Exchange</option>
                        {exchangeConfig?.exchanges.map(exchange => (
                            <option key={exchange} value={exchange}>
                                {exchange.toUpperCase()}
                            </option>
                        ))}
                    </select>
                </div>
                
                <div className="form-group">
                    <label>Currency Pair:</label>
                    <select 
                        value={selectedPair} 
                        onChange={e => setSelectedPair(e.target.value)}
                        disabled={!selectedExchange}
                    >
                        <option value="">Select Pair</option>
                        {availablePairs.map(pair => (
                            <option key={pair} value={pair}>{pair}</option>
                        ))}
                    </select>
                </div>
                
                <div className="form-group">
                    <label>Price Drop Threshold (%):</label>
                    <input 
                        type="number" 
                        name="priceDropThreshold" 
                        value={analysisParams.priceDropThreshold} 
                        onChange={handleParamChange}
                    />
                </div>
                
                <div className="form-group">
                    <label>Volume Increase Threshold (%):</label>
                    <input 
                        type="number" 
                        name="volumeIncreaseThreshold" 
                        value={analysisParams.volumeIncreaseThreshold} 
                        onChange={handleParamChange}
                    />
                </div>
                
                <div className="form-group">
                    <label>SMA Short Period:</label>
                    <input 
                        type="number" 
                        name="smaShortPeriod" 
                        value={analysisParams.smaShortPeriod} 
                        onChange={handleParamChange}
                    />
                </div>
                
                <div className="form-group">
                    <label>SMA Long Period:</label>
                    <input 
                        type="number" 
                        name="smaLongPeriod" 
                        value={analysisParams.smaLongPeriod} 
                        onChange={handleParamChange}
                    />
                </div>
                
                <button onClick={startAnalysis}>Start Analysis</button>
                <button onClick={stopAnalysis} style={{ marginLeft: '10px' }}>Stop Analysis</button>
            </div>
            
            <div className="analysis-results">
                <h3>Results</h3>
                {analysisResults.length > 0 ? (
                    <ul>
                        {analysisResults.map((result, index) => (
                            <li key={index}>
                                <pre>{JSON.stringify(result, null, 2)}</pre>
                            </li>
                        ))}
                    </ul>
                ) : (
                    <p>No results yet. Start an analysis to see results here.</p>
                )}
            </div>
        </div>
    );
};

export default Analysis;