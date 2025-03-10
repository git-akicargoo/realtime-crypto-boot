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
        
        // 카드의 임시 ID 사용
        const tempCardId = card.id;
        
        // 연결 ID는 임시 카드 ID 사용 (백엔드에서 최종 ID를 받으면 업데이트됨)
        let connectionId = tempCardId;
        
        // 카드의 생성 시간 가져오기
        const createdAt = card.getAttribute('data-created-at');
        const shortId = card.getAttribute('data-short-id');
        const baseId = card.getAttribute('data-base-id');
        
        console.log('연결 시작 - 임시 ID:', tempCardId, '기본 ID:', baseId, '생성 시간:', createdAt);
        
        // 이미 연결된 경우 재사용
        if (activeConnections[connectionId]) {
            console.log('기존 연결 재사용:', connectionId);
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
        
        // 웹소켓 URL 생성
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${window.location.host}/ws/analysis`;
        
        console.log('웹소켓 연결 시도:', wsUrl);
        
        // 웹소켓 연결 설정
        const ws = new WebSocket(wsUrl);
        
        // 연결 이벤트 처리
        ws.onopen = function() {
            console.log(`[${connectionId}] 웹소켓 연결됨`);
            connectionStatuses[connectionId] = 'CONNECTED';
            
            try {
                // 분석 시작 요청 전송
                const requestData = {
                    action: "startAnalysis",
                    exchange: exchange,
                    currencyPair: currencyPair,
                    symbol: symbol,
                    quoteCurrency: quoteCurrency,
                    tradingStyle: tradingStyle,
                    cardId: tempCardId,
                    shortId: shortId,
                    createdAt: createdAt
                };
                
                console.log(`[${connectionId}] 분석 요청 보내기:`, requestData);
                ws.send(JSON.stringify(requestData));
            } catch (error) {
                console.error(`[${connectionId}] 요청 전송 중 오류:`, error);
                showError(card, "분석 요청 전송 중 오류가 발생했습니다.");
            }
        };
        
        ws.onmessage = function(event) {
            try {
                const response = JSON.parse(event.data);
                console.log(`[${connectionId}] 웹소켓 응답 받음:`, response);
                
                // 응답에 카드 ID가 있는지 확인
                if (response.cardId) {
                    console.log(`[${connectionId}] 응답 카드 ID: ${response.cardId}, 현재 카드 ID: ${tempCardId}`);
                    
                    // 백엔드에서 받은 카드 ID가 현재 ID와 다른 경우 업데이트
                    if (response.cardId !== tempCardId && card) {
                        const backendCardId = response.cardId;
                        console.log(`[${connectionId}] 카드 ID 업데이트: ${tempCardId} -> ${backendCardId}`);
                        
                        // 카드 ID 업데이트
                        card.id = backendCardId;
                        
                        // 카드 ID 표시 업데이트
                        const cardIdDisplay = card.querySelector('.card-id-display');
                        if (cardIdDisplay) {
                            cardIdDisplay.textContent = `ID: ${backendCardId}`;
                        }
                        
                        // 연결 맵 업데이트
                        activeConnections[backendCardId] = ws;
                        delete activeConnections[connectionId];
                        
                        // 메시지 콜백 맵 업데이트
                        if (messageCallbacks[connectionId]) {
                            messageCallbacks[backendCardId] = messageCallbacks[connectionId];
                            delete messageCallbacks[connectionId];
                        }
                        
                        // connectionId 업데이트
                        connectionId = backendCardId;
                        
                        // 상태 객체 업데이트 (window.state.activeCards)
                        if (window.state && window.state.activeCards) {
                            if (window.state.activeCards[tempCardId]) {
                                window.state.activeCards[backendCardId] = window.state.activeCards[tempCardId];
                                window.state.activeCards[backendCardId].card = card;
                                delete window.state.activeCards[tempCardId];
                                console.log(`[${connectionId}] 상태 객체 업데이트 완료`);
                            }
                        }
                    }
                } else {
                    console.warn(`[${connectionId}] 응답에 카드 ID 없음`);
                }
                
                // 콜백 실행
                if (messageCallbacks[connectionId]) {
                    console.log(`[${connectionId}] 메시지 콜백 실행`);
                    messageCallbacks[connectionId](response);
                } else {
                    console.warn(`[${connectionId}] 메시지 콜백 없음`);
                    
                    // 카드 컴포넌트 직접 업데이트 시도 (콜백이 없는 경우)
                    if (card && window.CardComponent && window.CardComponent.updateCard) {
                        console.log(`[${connectionId}] 카드 컴포넌트 직접 업데이트 시도`);
                        window.CardComponent.updateCard(card, response);
                    }
                }
            } catch (error) {
                console.error(`[${connectionId}] 응답 처리 중 오류:`, error, event.data);
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