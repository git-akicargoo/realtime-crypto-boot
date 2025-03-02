// 웹소켓 서비스 모듈
const WebSocketService = (function() {
    // 활성 웹소켓 연결 저장
    const activeConnections = {};
    const connectionStatuses = {}; // 연결 상태 추적
    
    // 분석 시작 - tradingStyle 매개변수 사용
    function startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle = 'dayTrading') {
        console.log('분석 시작:', exchange, currencyPair, '모드:', tradingStyle);
        
        const connectionId = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 이미 웹소켓 연결이 있는지 확인
        if (activeConnections[connectionId]) {
            console.log('이미 웹소켓 연결이 있습니다.');
            return;
        }
        
        // 로딩 표시 시작
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'flex';
        }
        
        // 시작 버튼 숨기고 중지 버튼 표시
        const startButton = card.querySelector('.start-button');
        const stopButton = card.querySelector('.stop-button');
        if (startButton && stopButton) {
            startButton.style.display = 'none';
            stopButton.style.display = 'inline-block';
        }
        
        // 웹소켓 URL 설정 - 환경에 따라 조정
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsHost = window.location.hostname === 'localhost' ? 'localhost:8080' : window.location.host;
        const wsUrl = `${wsProtocol}//${wsHost}/ws/analysis`;
        
        // 웹소켓 연결 상태 초기화
        connectionStatuses[connectionId] = {
            connected: false,
            reconnectAttempts: 0,
            lastError: null
        };
        
        // 웹소켓 연결
        const ws = new WebSocket(wsUrl);
        activeConnections[connectionId] = ws;
        
        ws.onopen = function() {
            console.log('웹소켓 연결됨:', wsUrl);
            connectionStatuses[connectionId].connected = true;
            
            // 메시지 형식 수정: command와 data 필드 추가
            const request = {
                command: "start",  // command 필드 추가
                data: {            // 모든 데이터를 data 객체 안에 포함
                    exchange: exchange,
                    currencyPair: currencyPair,
                    symbol: symbol,
                    quoteCurrency: quoteCurrency,
                    tradingStyle: tradingStyle,
                    rsiPeriod: 14,
                    rsiOverbought: 70,
                    rsiOversold: 30,
                    bollingerPeriod: 20,
                    bollingerDeviation: 2
                }
            };
            
            console.log('분석 요청 전송:', request);
            ws.send(JSON.stringify(request));
        };
        
        ws.onmessage = function(event) {
            console.log(`[${connectionId}] 데이터 수신:`, event.data);
            
            try {
                const data = JSON.parse(event.data);
                
                // 로딩 표시 숨기기
                if (loadingIndicator) {
                    loadingIndicator.style.display = 'none';
                }
                
                // 수신된 데이터를 카드에 업데이트
                CardComponent.updateCard(card, data);
                
                // 대시보드에도 데이터 추가
                if (window.DashboardComponent) {
                    window.DashboardComponent.addDataPoint(data);
                }
            } catch (error) {
                console.error('데이터 처리 오류:', error);
                CardComponent.showError(card, '데이터 처리 중 오류가 발생했습니다.');
            }
        };
        
        ws.onerror = function(error) {
            console.error(`[${connectionId}] 웹소켓 오류:`, error);
            connectionStatuses[connectionId].lastError = error;
            CardComponent.showError(card, '웹소켓 연결 오류가 발생했습니다.');
        };
        
        ws.onclose = function(event) {
            console.log(`[${connectionId}] 웹소켓 연결 종료:`, event.code, event.reason);
            
            // 의도적으로 닫지 않았다면 재연결 시도
            if (connectionStatuses[connectionId] && 
                connectionStatuses[connectionId].connected && 
                connectionStatuses[connectionId].reconnectAttempts < 3) {
                
                connectionStatuses[connectionId].reconnectAttempts++;
                console.log(`[${connectionId}] 재연결 시도 ${connectionStatuses[connectionId].reconnectAttempts}/3...`);
                
                // 1초 후 재연결 시도
                setTimeout(() => {
                    startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
                }, 1000);
            } else {
                // 재연결 실패 또는 의도적 종료
                delete activeConnections[connectionId];
                delete connectionStatuses[connectionId];
                
                // 버튼 상태 복원
                if (startButton && stopButton) {
                    startButton.style.display = 'inline-block';
                    stopButton.style.display = 'none';
                }
                
                // 재시도 버튼 표시
                const retryButton = card.querySelector('.retry-button');
                if (retryButton) {
                    retryButton.style.display = 'inline-block';
                }
            }
        };
        
        // 중지 버튼 클릭 시 이벤트
        if (stopButton) {
            stopButton.addEventListener('click', function() {
                if (ws && ws.readyState === WebSocket.OPEN) {
                    // 중지 명령도 command 형식에 맞게 수정
                    ws.send(JSON.stringify({
                        command: "stop",
                        data: { 
                            exchange: exchange,
                            currencyPair: currencyPair
                        }
                    }));
                }
                closeConnection(connectionId);
                
                // UI 업데이트
                this.style.display = 'none';
                const startButton = card.querySelector('.start-button');
                if (startButton) startButton.style.display = 'inline-block';
                
                const loadingIndicator = card.querySelector('.loading-indicator');
                if (loadingIndicator) loadingIndicator.style.display = 'none';
            });
        }
        
        return ws;
    }
    
    // 분석 중지
    function stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card) {
        console.log('분석 중지:', exchange, currencyPair);
        
        const connectionId = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 웹소켓이 열려있는 경우 중지 명령 전송
        const connection = activeConnections[connectionId];
        if (connection && connection.socket && connection.socket.readyState === WebSocket.OPEN) {
            connection.socket.send(JSON.stringify({
                command: "stop",
                data: { 
                    exchange: exchange,
                    currencyPair: currencyPair
                }
            }));
        }
        
        // 연결 종료
        closeConnection(connectionId);
        
        // UI 업데이트 (card가 제공된 경우에만)
        if (card) {
            const startBtn = card.querySelector('.start-button');
            const stopBtn = card.querySelector('.stop-button');
            const retryBtn = card.querySelector('.retry-button');
            const loadingIndicator = card.querySelector('.loading-indicator');
            
            if (startBtn) startBtn.style.display = 'inline-block';
            if (stopBtn) stopBtn.style.display = 'none';
            if (retryBtn) retryBtn.style.display = 'none';
            if (loadingIndicator) loadingIndicator.style.display = 'none';
        }
    }
    
    // 웹소켓 연결 종료
    function closeConnection(connectionId) {
        console.log(`[${connectionId}] 연결 종료 시도...`);
        
        if (activeConnections[connectionId]) {
            if (activeConnections[connectionId].readyState === WebSocket.OPEN ||
                activeConnections[connectionId].readyState === WebSocket.CONNECTING) {
                
                console.log(`[${connectionId}] 웹소켓 연결 닫기 중...`);
                activeConnections[connectionId].close();
            }
            
            // 웹소켓 객체 삭제 (중요!)
            delete activeConnections[connectionId];
            console.log(`[${connectionId}] activeConnections에서 제거됨`);
        }
        
        if (connectionStatuses[connectionId]) {
            connectionStatuses[connectionId].connected = false;
            delete connectionStatuses[connectionId];
            console.log(`[${connectionId}] 연결 상태 정보 제거됨`);
        }
    }
    
    // 모든 연결 종료 (페이지 이탈 시)
    function closeAllConnections() {
        for (const id in activeConnections) {
            closeConnection(id);
        }
    }
    
    // 페이지 이탈 시 모든 연결 종료
    window.addEventListener('beforeunload', closeAllConnections);
    
    // 공개 API
    return {
        startAnalysis,
        stopAnalysis,
        closeConnection,
        closeAllConnections
    };
})(); 