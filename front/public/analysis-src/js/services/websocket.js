// 웹소켓 서비스 모듈
const WebSocketService = (function() {
    // 활성 STOMP 클라이언트 저장
    const activeConnections = {};
    const connectionStatuses = {}; // 연결 상태 추적
    
    // 메시지 콜백 저장
    const messageCallbacks = {};
    
    // 구독 객체 저장
    const subscriptions = {};
    
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
        
        // STOMP 웹소켓 URL 생성
        const serverUrl = 'http://localhost:8080';
        console.log('STOMP 연결 시도:', serverUrl);
        
        // STOMP 클라이언트 생성
        const stompClient = new StompJs.Client({
            webSocketFactory: () => new SockJS(`${serverUrl}/ws/stomp/analysis`),
            debug: function(str) {
                console.log('STOMP: ' + str);
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000
        });
        
        // STOMP 연결 이벤트 처리
        stompClient.onConnect = function(frame) {
            console.log(`[${connectionId}] STOMP 연결됨`);
            connectionStatuses[connectionId] = 'CONNECTED';
            
            try {
                // 카드별 토픽 생성
                const cardSpecificTopic = `/topic/analysis.${tempCardId}`;
                console.log(`[${connectionId}] 카드별 토픽 구독: ${cardSpecificTopic}`);
                
                // 카드별 분석 결과 구독
                const cardSubscription = stompClient.subscribe(cardSpecificTopic, function(message) {
                    try {
                        const response = JSON.parse(message.body);
                        console.log(`[${connectionId}] STOMP 응답 받음 (카드별 토픽):`, response);
                        
                        processAnalysisResponse(message, response, connectionId, tempCardId, card);
                    } catch (error) {
                        console.error(`[${connectionId}] 응답 처리 중 오류 (카드별 토픽):`, error, message.body);
                    }
                });
                
                // 구독 객체 저장
                subscriptions[connectionId] = {
                    cardSpecific: cardSubscription
                };
                
                // 하위 호환성을 위한 일반 토픽 구독 (모든 메시지 수신)
                // 이 구독은 이전 버전과의 호환성을 위해 유지하지만 현재 카드에 해당하는 메시지만 처리
                const generalSubscription = stompClient.subscribe('/topic/analysis', function(message) {
                    try {
                        const response = JSON.parse(message.body);
                        
                        // 이 카드에 해당하는 메시지인지 확인
                        if (response.cardId === connectionId) {
                            console.log(`[${connectionId}] STOMP 응답 받음 (일반 토픽, 이 카드용):`, response);
                            processAnalysisResponse(message, response, connectionId, tempCardId, card);
                        } else {
                            // 다른 카드에 대한 메시지는 무시
                            console.debug(`[${connectionId}] 다른 카드에 대한 응답 무시: ${response.cardId}`);
                        }
                    } catch (error) {
                        console.error(`[${connectionId}] 응답 처리 중 오류 (일반 토픽):`, error, message.body);
                    }
                });
                
                // 일반 구독 객체 저장
                if (subscriptions[connectionId]) {
                    subscriptions[connectionId].general = generalSubscription;
                }
                
                // 분석 시작 요청 전송
                const requestData = {
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
                stompClient.publish({
                    destination: '/app/analysis.start',
                    body: JSON.stringify(requestData)
                });
                
            } catch (error) {
                console.error(`[${connectionId}] 요청 전송 중 오류:`, error);
                showError(card, "분석 요청 전송 중 오류가 발생했습니다.");
            }
        };
        
        stompClient.onStompError = function(frame) {
            console.error(`[${connectionId}] STOMP 오류:`, frame);
            connectionStatuses[connectionId].lastError = frame;
            showError(card, 'STOMP 연결 오류가 발생했습니다.');
        };
        
        stompClient.onWebSocketClose = function() {
            console.log(`[${connectionId}] 웹소켓 연결 종료`);
            
            // 의도적으로 닫지 않았다면 자동 재연결 (STOMP 클라이언트가 처리)
            if (!stompClient.deactivated) {
                console.log(`[${connectionId}] 재연결 시도...`);
            } else {
                // 의도적 종료
                delete activeConnections[connectionId];
                delete connectionStatuses[connectionId];
                delete subscriptions[connectionId];
                
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
        
        // STOMP 연결 시작
        stompClient.activate();
        
        // 연결 저장
        activeConnections[connectionId] = stompClient;
        return stompClient;
    }
    
    // 분석 응답 처리 함수
    function processAnalysisResponse(message, response, connectionId, tempCardId, card) {
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
                activeConnections[backendCardId] = activeConnections[connectionId];
                delete activeConnections[connectionId];
                
                // 구독 맵 업데이트
                if (subscriptions[connectionId]) {
                    subscriptions[backendCardId] = subscriptions[connectionId];
                    delete subscriptions[connectionId];
                }
                
                // 메시지 콜백 맵 업데이트
                if (messageCallbacks[connectionId]) {
                    messageCallbacks[backendCardId] = messageCallbacks[connectionId];
                    delete messageCallbacks[connectionId];
                }
                
                // connectionId 업데이트
                connectionId = backendCardId;
                
                // 상태 객체 업데이트
                if (window.state && window.state.activeCards) {
                    if (window.state.activeCards[tempCardId]) {
                        window.state.activeCards[backendCardId] = window.state.activeCards[tempCardId];
                        window.state.activeCards[backendCardId].card = card;
                        delete window.state.activeCards[tempCardId];
                        console.log(`[${connectionId}] 상태 객체 업데이트 완료`);
                    }
                }
            }
        }
        
        // 콜백 실행
        if (messageCallbacks[connectionId]) {
            console.log(`[${connectionId}] 메시지 콜백 실행`);
            messageCallbacks[connectionId](response);
        } else {
            console.warn(`[${connectionId}] 메시지 콜백 없음`);
            
            // 카드 컴포넌트 직접 업데이트 시도
            if (card && window.CardComponent && window.CardComponent.updateCard) {
                console.log(`[${connectionId}] 카드 컴포넌트 직접 업데이트 시도`);
                window.CardComponent.updateCard(card, response);
            }
        }
    }
    
    // 분석 중지
    function stopAnalysis(cardId) {
        console.log('분석 중지:', cardId);
        
        const connectionId = cardId;
        
        // STOMP 클라이언트가 있는지 확인
        if (!activeConnections[connectionId]) {
            console.log('중지할 STOMP 연결이 없습니다.');
            return;
        }
        
        const stompClient = activeConnections[connectionId];
        
        // 연결이 활성화되어 있으면 중지 메시지 전송
        if (stompClient.connected) {
            // 원래 카드 ID에서 exchange와 currencyPair 추출
            const parts = connectionId.split('-');
            const exchange = parts[0];
            // 나머지 부분을 currencyPair로 사용 (UUID 부분 제외)
            const currencyPair = parts.slice(1, parts.length - 1).join('-');
            
            const stopRequest = {
                exchange: exchange,
                currencyPair: currencyPair,
                cardId: connectionId
            };
            
            console.log('중지 요청 전송:', stopRequest);
            stompClient.publish({
                destination: '/app/analysis.stop',
                body: JSON.stringify(stopRequest)
            });
        }
        
        // 구독 정리
        if (subscriptions[connectionId]) {
            if (subscriptions[connectionId].cardSpecific) {
                subscriptions[connectionId].cardSpecific.unsubscribe();
            }
            if (subscriptions[connectionId].general) {
                subscriptions[connectionId].general.unsubscribe();
            }
            delete subscriptions[connectionId];
        }
        
        // STOMP 연결 종료
        stompClient.deactivate();
        delete activeConnections[connectionId];
        
        // 메시지 콜백 제거
        if (messageCallbacks[connectionId]) {
            delete messageCallbacks[connectionId];
        }
    }
    
    // 메시지 수신 콜백 등록
    function onMessage(cardId, callback) {
        console.log(`[${cardId}] 메시지 콜백 등록`);
        messageCallbacks[cardId] = callback;
    }
    
    // 연결 종료
    function closeConnection(connectionId) {
        if (activeConnections[connectionId]) {
            console.log(`[${connectionId}] 연결 종료`);
            
            // 구독 정리
            if (subscriptions[connectionId]) {
                if (subscriptions[connectionId].cardSpecific) {
                    subscriptions[connectionId].cardSpecific.unsubscribe();
                }
                if (subscriptions[connectionId].general) {
                    subscriptions[connectionId].general.unsubscribe();
                }
                delete subscriptions[connectionId];
            }
            
            activeConnections[connectionId].deactivate();
            delete activeConnections[connectionId];
        }
    }
    
    // 모든 연결 종료
    function closeAllConnections() {
        console.log('모든 연결 종료');
        Object.keys(activeConnections).forEach(closeConnection);
    }
    
    // 오류 표시 함수
    function showError(card, message) {
        const errorDisplay = card.querySelector('.error-display');
        if (errorDisplay) {
            errorDisplay.textContent = message;
            errorDisplay.style.display = 'block';
        }
        
        // 로딩 표시 숨기기
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'none';
        }
    }
    
    return {
        init,
        startAnalysis,
        stopAnalysis,
        onMessage,
        closeConnection,
        closeAllConnections
    };
})();

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    WebSocketService.init();
}); 