// 웹소켓 서비스 모듈
const WebSocketService = (function() {
    // 활성 웹소켓 연결 저장
    const activeConnections = {};
    
    // 분석 시작
    function startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card) {
        console.log('분석 시작:', exchange, currencyPair);
        
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
        
        // 웹소켓 연결
        const ws = new WebSocket(wsUrl);
        activeConnections[connectionId] = ws;
        
        ws.onopen = function() {
            console.log('웹소켓 연결됨:', wsUrl);
            
            // 분석 요청 전송
            const request = {
                exchange,
                currencyPair,
                symbol,
                quoteCurrency
            };
            
            console.log('분석 요청 전송:', request);
            ws.send(JSON.stringify(request));
        };
        
        ws.onmessage = function(event) {
            try {
                const data = JSON.parse(event.data);
                console.log('분석 데이터 수신:', data);
                
                // 에러 체크
                if (data.error) {
                    console.error('백엔드 에러:', data.error, data.details);
                    CardComponent.showError(card, `분석 중 오류: ${data.error}`);
                    
                    // 연결 정리
                    closeConnection(connectionId);
                    return;
                }
                
                // 데이터 처리 및 카드 업데이트
                CardComponent.updateCard(card, data);
                
                // 대시보드 업데이트 (추후 구현)
                if (window.DashboardComponent) {
                    window.DashboardComponent.addDataPoint(data);
                }
                
            } catch (error) {
                console.error('메시지 처리 중 오류:', error);
                CardComponent.showError(card, '데이터 처리 중 오류가 발생했습니다.');
                
                // 연결 정리
                closeConnection(connectionId);
            }
        };
        
        ws.onerror = function(error) {
            console.error('웹소켓 오류:', error);
            CardComponent.showError(card, '서버 연결 중 오류가 발생했습니다.');
            
            // 연결 정리
            closeConnection(connectionId);
        };
        
        ws.onclose = function() {
            console.log('웹소켓 연결 종료:', connectionId);
            
            // 연결 정리
            delete activeConnections[connectionId];
            
            // 버튼 상태 복원 (이미 에러 처리되지 않은 경우에만)
            const retryButton = card.querySelector('.retry-button');
            if (retryButton && retryButton.style.display !== 'inline-block') {
                const startButton = card.querySelector('.start-button');
                const stopButton = card.querySelector('.stop-button');
                
                if (startButton && stopButton) {
                    startButton.style.display = 'inline-block';
                    stopButton.style.display = 'none';
                }
            }
        };
    }
    
    // 분석 중지
    function stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card) {
        const connectionId = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 웹소켓 연결 확인 및 종료
        closeConnection(connectionId);
        
        // 버튼 상태 복원
        const startButton = card.querySelector('.start-button');
        const stopButton = card.querySelector('.stop-button');
        
        if (startButton && stopButton) {
            startButton.style.display = 'inline-block';
            stopButton.style.display = 'none';
        }
        
        console.log('분석 중지:', connectionId);
    }
    
    // 웹소켓 연결 종료
    function closeConnection(connectionId) {
        if (activeConnections[connectionId]) {
            if (activeConnections[connectionId].readyState === WebSocket.OPEN) {
                activeConnections[connectionId].close();
            }
            delete activeConnections[connectionId];
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