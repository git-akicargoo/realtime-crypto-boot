// 웹소켓 서비스 모듈
const WebSocketService = (function() {
    // 활성 웹소켓 연결 저장
    const activeConnections = {};
    const connectionStatuses = {}; // 연결 상태 추적
    
    // 메시지 콜백 저장
    const messageCallbacks = {};
    
    // 초기화 함수
    function init() {
        console.log('WebSocketService 초기화');
        // 페이지 이탈 시 모든 연결 종료
        window.addEventListener('beforeunload', closeAllConnections);
    }
    
    // 분석 시작 - tradingStyle 매개변수 사용
    function startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle = 'dayTrading') {
        console.log('분석 시작:', exchange, currencyPair, '모드:', tradingStyle);
        
        const connectionId = `${exchange}-${currencyPair}`.toLowerCase();
        console.log('연결 ID:', connectionId);
        
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
        
        console.log('웹소켓 URL:', wsUrl);
        
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
                
                // 카드 ID 가져오기
                const cardId = card.id;
                
                // 등록된 콜백이 있으면 호출
                if (messageCallbacks[cardId]) {
                    console.log(`[${cardId}] 메시지 콜백 호출`);
                    messageCallbacks[cardId](data);
                } else {
                    // 기존 방식으로 카드 업데이트
                    console.log(`[${cardId}] 기본 업데이트 사용`);
                    
                    // 카드 컴포넌트의 processWebSocketMessage 함수 직접 호출
                    if (window.CardComponent && typeof window.CardComponent.processWebSocketMessage === 'function') {
                        console.log(`[${cardId}] processWebSocketMessage 함수 호출`);
                        try {
                            window.CardComponent.processWebSocketMessage(card, data);
                        } catch (error) {
                            console.error(`[${cardId}] processWebSocketMessage 함수 호출 중 오류 발생:`, error);
                            // 오류 발생 시 기본 updateCard 함수로 폴백
                            if (window.CardComponent && typeof window.CardComponent.updateCard === 'function') {
                                console.log(`[${cardId}] 폴백: updateCard 함수 호출`);
                                window.CardComponent.updateCard(card, data);
                            }
                        }
                    } else {
                        // 기존 updateCard 함수 호출
                        console.log(`[${cardId}] updateCard 함수 호출`);
                        if (window.CardComponent) {
                            window.CardComponent.updateCard(card, data);
                        } else {
                            console.error(`[${cardId}] CardComponent를 찾을 수 없습니다.`);
                        }
                    }
                }
                
                // 대시보드에도 데이터 추가
                if (window.DashboardComponent) {
                    window.DashboardComponent.addDataPoint(data);
                }
            } catch (error) {
                console.error('데이터 처리 오류:', error);
                showError(card, '데이터 처리 중 오류가 발생했습니다.');
            }
        };
        
        ws.onerror = function(error) {
            console.error(`[${connectionId}] 웹소켓 오류:`, error);
            connectionStatuses[connectionId].lastError = error;
            showError(card, '웹소켓 연결 오류가 발생했습니다.');
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
        
        return ws;
    }
    
    // 분석 중지
    function stopAnalysis(cardId) {
        console.log('분석 중지:', cardId);
        
        // 카드 ID에서 연결 ID 추출
        const parts = cardId.split('-');
        if (parts.length < 2) {
            console.error('유효하지 않은 카드 ID:', cardId);
            return;
        }
        
        const exchange = parts[0];
        const currencyPair = parts.slice(1).join('-');
        const connectionId = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 웹소켓 연결이 있는지 확인
        if (!activeConnections[connectionId]) {
            console.log('중지할 웹소켓 연결이 없습니다.');
            return;
        }
        
        const ws = activeConnections[connectionId];
        
        // 연결이 열려 있으면 중지 메시지 전송
        if (ws && ws.readyState === WebSocket.OPEN) {
            // 중지 명령도 command 형식에 맞게 수정
            const stopRequest = {
                command: "stop",
                data: { 
                    exchange: exchange,
                    currencyPair: currencyPair
                }
            };
            
            console.log('중지 요청 전송:', stopRequest);
            ws.send(JSON.stringify(stopRequest));
        }
        
        // 연결 종료
        closeConnection(connectionId);
        
        // 메시지 콜백 제거
        if (messageCallbacks[cardId]) {
            delete messageCallbacks[cardId];
        }
    }
    
    // 메시지 수신 콜백 등록
    function onMessage(cardId, callback) {
        console.log(`[${cardId}] 메시지 콜백 등록`);
        if (typeof callback === 'function') {
            messageCallbacks[cardId] = callback;
        } else {
            console.error(`[${cardId}] 유효하지 않은 콜백 함수`);
        }
    }
    
    // 웹소켓 연결 종료
    function closeConnection(connectionId) {
        if (activeConnections[connectionId]) {
            console.log('웹소켓 연결 종료:', connectionId);
            
            try {
                // 웹소켓 연결 종료
                activeConnections[connectionId].close(1000, '사용자에 의한 연결 종료');
            } catch (error) {
                console.error('웹소켓 종료 중 오류:', error);
            }
            
            // 연결 정보 제거
            delete activeConnections[connectionId];
            delete connectionStatuses[connectionId];
        }
    }
    
    // 모든 웹소켓 연결 종료
    function closeAllConnections() {
        console.log('모든 웹소켓 연결 종료');
        
        // 모든 연결 종료
        Object.keys(activeConnections).forEach(connectionId => {
            try {
                activeConnections[connectionId].close(1000, '사용자에 의한 연결 종료');
            } catch (error) {
                console.error(`[${connectionId}] 웹소켓 종료 중 오류:`, error);
            }
        });
        
        // 연결 정보 초기화
        Object.keys(activeConnections).forEach(key => delete activeConnections[key]);
        Object.keys(connectionStatuses).forEach(key => delete connectionStatuses[key]);
        
        // 메시지 콜백 초기화
        Object.keys(messageCallbacks).forEach(key => delete messageCallbacks[key]);
    }
    
    // 오류 표시
    function showError(card, errorMessage) {
        console.error('카드 오류:', errorMessage);
        
        // 로딩 표시 숨기기
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'none';
        }
        
        // 버튼 상태 변경
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const retryBtn = card.querySelector('.retry-button');
        
        if (startBtn && stopBtn && retryBtn) {
            startBtn.style.display = 'none';
            stopBtn.style.display = 'none';
            retryBtn.style.display = 'inline-block';
        }
        
        // 오류 메시지 표시
        const messageElement = card.querySelector('.analysis-message');
        if (messageElement) {
            messageElement.textContent = errorMessage || '분석 중 오류가 발생했습니다.';
            messageElement.style.color = 'var(--negative-color)';
        }
    }
    
    // 공개 API
    return {
        init,
        startAnalysis,
        stopAnalysis,
        onMessage,
        closeConnection,
        closeAllConnections,
        showError
    };
})();

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    WebSocketService.init();
}); 