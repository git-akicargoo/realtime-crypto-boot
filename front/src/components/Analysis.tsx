import React, { useState, useEffect, useRef } from 'react';

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
    const socketRef = useRef<WebSocket | null>(null);

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

    // 웹소켓 연결
    useEffect(() => {
        const socket = new WebSocket('ws://localhost:8080/ws/analysis');
        
        socket.onopen = () => {
            console.log('WebSocket connected');
        };
        
        socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                setAnalysisResults(prev => [...prev, data]);
            } catch (e) {
                console.error('Failed to parse WebSocket message:', e);
            }
        };
        
        socket.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
        
        socket.onclose = () => {
            console.log('WebSocket disconnected');
        };
        
        socketRef.current = socket;
        
        return () => {
            socket.close();
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
            exchange: selectedExchange,
            currencyPair: selectedPair,
            ...analysisParams
        };
        
        if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
            console.log('Sending analysis request:', request);
            socketRef.current.send(JSON.stringify(request));
        } else {
            console.error('WebSocket not connected');
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