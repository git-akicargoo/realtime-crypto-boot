// 웹소켓 서비스 모듈
const WebSocketService = (function() {
    // 활성 웹소켓 연결 저장
    const activeConnections = {};
    
    // 분석 시작
    function startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card) {
        console.log('분석 시작 요청:', exchange, currencyPair);
        
        // 로딩 표시
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'flex';
        }
        
        // 웹소켓 연결
        const ws = new WebSocket(`ws://${window.location.hostname}:8080/ws/analysis`);
        
        // 연결 저장 (카드 객체를 키로 사용)
        const connectionKey = card;
        activeConnections[connectionKey] = ws;
        
        ws.onopen = function() {
            console.log('웹소켓 연결 성공');
            
            // 분석 요청 전송
            const request = {
                action: 'startAnalysis',
                exchange: exchange,
                currencyPair: currencyPair,
                symbol: symbol,
                quoteCurrency: quoteCurrency,
                tradingStyle: 'dayTrading'  // 기본값
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
                    console.error('백엔드 에러:', data.error);
                    CardComponent.showError(card, `분석 중 오류: ${data.error}`);
                    
                    // 연결 정리
                    closeConnection(connectionKey);
                    return;
                }
                
                // 백엔드에서 생성된 카드 ID와 타임스탬프 사용
                const cardId = data.cardId;
                const timestamp = data.timestamp;
                
                // 상세 로그 추가: 백엔드에서 받은 카드 ID
                console.log('백엔드에서 받은 원본 카드 ID:', cardId);
                console.log('백엔드에서 받은 타임스탬프:', timestamp);
                
                // 카드 ID 설정
                if (cardId && !card.id) {
                    // 카드 ID 설정
                    card.id = cardId;
                    
                    // 카드 ID 업데이트 이벤트 발생
                    card.dispatchEvent(new CustomEvent('cardIdUpdated', {
                        detail: { cardId: cardId, timestamp: timestamp }
                    }));
                    
                    console.log('카드 ID 설정:', cardId);
                    
                    // 연결 맵 업데이트
                    activeConnections[cardId] = ws;
                    delete activeConnections[connectionKey];
                    connectionKey = cardId;
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
                closeConnection(connectionKey);
            }
        };
        
        ws.onerror = function(error) {
            console.error('웹소켓 오류:', error);
            CardComponent.showError(card, '서버 연결 중 오류가 발생했습니다.');
            
            // 연결 정리
            closeConnection(connectionKey);
        };
        
        ws.onclose = function() {
            console.log('웹소켓 연결 종료');
            
            // 연결 정리
            delete activeConnections[connectionKey];
            
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
        // 웹소켓 연결 확인 및 종료
        if (card && card.id) {
            closeConnection(card.id);
        } else {
            closeConnection(card);
        }
        
        // 버튼 상태 복원
        const startButton = card.querySelector('.start-button');
        const stopButton = card.querySelector('.stop-button');
        
        if (startButton && stopButton) {
            startButton.style.display = 'inline-block';
            stopButton.style.display = 'none';
        }
        
        console.log('분석 중지');
    }
    
    // 웹소켓 연결 종료
    function closeConnection(connectionKey) {
        if (activeConnections[connectionKey]) {
            if (activeConnections[connectionKey].readyState === WebSocket.OPEN) {
                activeConnections[connectionKey].close();
            }
            delete activeConnections[connectionKey];
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