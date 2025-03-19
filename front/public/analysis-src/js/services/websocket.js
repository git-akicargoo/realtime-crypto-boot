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
        
        // exchange-currencyPair로 시작하는 연결이 있는지 확인
        const prefix = `${exchange.toLowerCase()}-${currencyPair.toLowerCase()}`;
        const existingConnection = Object.keys(activeConnections).find(id => id.startsWith(prefix));
        
        if (existingConnection) {
            console.log(`${prefix}로 시작하는 연결이 이미 있습니다: ${existingConnection}`);
            // 기존 연결 종료
            stopAnalysis(existingConnection);
            console.log(`기존 연결 종료 후 새 연결 시작`);
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
                
                // 추가: 모든 카드 ID 패턴 구독 (백엔드에서 생성하는 새 ID에 대응)
                const allCardsTopic = `/topic/analysis.*`;
                console.log(`[${connectionId}] 모든 카드 토픽 구독: ${allCardsTopic}`);
                
                const allCardsSubscription = stompClient.subscribe(allCardsTopic, function(message) {
                    try {
                        const response = JSON.parse(message.body);
                        
                        // 이 카드의 exchange와 currencyPair와 일치하는지 확인
                        const msgExchange = response.exchange?.toLowerCase();
                        const msgCurrencyPair = response.currencyPair?.toLowerCase();
                        const cardExchange = exchange.toLowerCase();
                        const cardCurrencyPair = currencyPair.toLowerCase();
                        
                        if (msgExchange === cardExchange && msgCurrencyPair === cardCurrencyPair) {
                            console.log(`[${connectionId}] 일치하는 exchange/currencyPair 메시지 발견:`, response);
                            processAnalysisResponse(message, response, connectionId, tempCardId, card);
                        }
                    } catch (error) {
                        console.error(`[${connectionId}] 응답 처리 중 오류 (모든 카드 토픽):`, error, message.body);
                    }
                });
                
                // 구독 객체 저장
                subscriptions[connectionId] = {
                    cardSpecific: cardSubscription,
                    allCards: allCardsSubscription
                };
                
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
                
                // 중요: 실제 연결 객체 가져오기
                const stompClient = activeConnections[connectionId];
                
                // 연결 맵 업데이트
                if (stompClient) {
                    console.log(`[${connectionId}] 연결 맵 업데이트: ${tempCardId} -> ${backendCardId}`);
                    activeConnections[backendCardId] = stompClient;
                    delete activeConnections[connectionId];
                } else {
                    console.error(`[${connectionId}] 연결 객체를 찾을 수 없음`);
                }
                
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
                
                // 디버깅: 업데이트 후 activeConnections 출력
                console.log(`[${connectionId}] 업데이트 후 활성 연결 목록:`, Object.keys(activeConnections));
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
    function stopAnalysis(exchangeOrCardId, currencyPair, symbol, quoteCurrency, card) {
        // 여러 매개변수가 있는 경우 (app.js에서 호출하는 형태)
        if (arguments.length >= 2) {
            const exchange = exchangeOrCardId; // 첫 번째 매개변수는 exchange
            
            console.log('분석 중지 (exchange,pair):', exchange, currencyPair);
            
            // cardId가 없는 경우, exchange와 currencyPair로 검색
            const connectionIds = Object.keys(activeConnections);
            const prefix = `${exchange.toLowerCase()}-${currencyPair.toLowerCase()}`;
            const matchingId = connectionIds.find(id => id.startsWith(prefix));
            
            if (matchingId) {
                console.log(`연결 ID로 stopAnalysis 실행: ${matchingId}`);
                // 재귀적으로 cardId 버전의 함수 호출
                stopAnalysis(matchingId);
                return;
            } else {
                console.error(`해당하는 연결을 찾을 수 없음: ${prefix}`);
                
                // 카드 ID가 있는 경우 (card 객체가 전달된 경우)
                if (card && card.id) {
                    console.log(`카드 ID를 사용하여 중지 시도: ${card.id}`);
                    stopAnalysis(card.id);
                    return;
                }
                
                return;
            }
        }
        
        // 매개변수가 하나만 있는 경우 (cardId만 전달된 경우)
        if (arguments.length === 1 && typeof exchangeOrCardId === 'string') {
            console.log('분석 중지 (cardId):', exchangeOrCardId);
            
            // 디버깅: 모든 활성 연결 출력
            console.log('활성 연결 목록:', Object.keys(activeConnections));
            
            const connectionId = exchangeOrCardId;
            
            // 1. 정확한 ID로 찾기
            if (activeConnections[connectionId]) {
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
                    if (subscriptions[connectionId].allCards) {
                        subscriptions[connectionId].allCards.unsubscribe();
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
                
                console.log(`연결 ${connectionId} 성공적으로 종료됨`);
                return;
            }
            
            // 2. ID가 정확히 일치하지 않는 경우, 부분 일치로 찾기
            console.log('중지할 STOMP 연결이 없습니다. 유사한 연결 찾기 시도...');
            
            // 부분 ID 추출 (exchange-currencyPair 부분)
            const idParts = connectionId.split('-');
            // 마지막 부분 (UUID)을 제외한 부분
            const baseConnectionId = idParts.slice(0, -1).join('-').toLowerCase();
            
            console.log(`기본 연결 ID로 검색: ${baseConnectionId}`);
            
            // 모든 활성 연결 검사
            const connectionIds = Object.keys(activeConnections);
            
            // 우선 시작 부분이 일치하는 연결 찾기
            const exactPrefixMatch = connectionIds.find(id => 
                id.toLowerCase().startsWith(baseConnectionId));
                
            if (exactPrefixMatch) {
                console.log(`기본 ID로 정확한 연결 찾음: ${exactPrefixMatch}`);
                stopAnalysis(exactPrefixMatch);
                return;
            }
            
            // 부분 일치하는 연결 찾기
            const partialMatch = connectionIds.find(id => {
                // exchange 부분 추출 (첫 번째 부분)
                const idExchange = id.split('-')[0].toLowerCase();
                const targetExchange = baseConnectionId.split('-')[0].toLowerCase();
                
                // 적어도 exchange는 일치해야 함
                return idExchange === targetExchange && 
                      (id.includes(baseConnectionId) || baseConnectionId.includes(id));
            });
            
            if (partialMatch) {
                console.log(`부분 일치하는 연결 찾음: ${partialMatch}`);
                // 재귀적으로 호출하여 이 ID로 다시 시도
                stopAnalysis(partialMatch);
                return;
            }
            
            // 모든 시도 실패
            console.log('일치하는 연결을 찾을 수 없음. 분석이 이미 중지되었거나 실행 중이 아닌 것 같습니다.');
        } else {
            console.error('올바르지 않은 매개변수:', arguments);
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
                if (subscriptions[connectionId].allCards) {
                    subscriptions[connectionId].allCards.unsubscribe();
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
    
    // 요청 전송 함수 (card.js에서 호출하는 함수)
    function send(request) {
        if (request && request.action === 'stopAnalysis') {
            // 요청에 cardId가 있으면 직접 사용
            if (request.cardId && activeConnections[request.cardId]) {
                console.log(`직접 지정된 cardId로 stopAnalysis 실행: ${request.cardId}`);
                stopAnalysis(request.cardId);
                return;
            }
            
            // cardId가 없거나 유효하지 않은 경우, exchange와 currencyPair로 검색
            if (request.exchange && request.currencyPair) {
                console.log(`exchange와 currencyPair로 stopAnalysis 실행: ${request.exchange}, ${request.currencyPair}`);
                stopAnalysis(request.exchange, request.currencyPair);
                return;
            }
            
            console.error('요청에 필요한 정보가 부족합니다:', request);
        } else {
            console.error('지원되지 않는 요청:', request);
        }
    }
    
    return {
        init,
        startAnalysis,
        stopAnalysis,
        onMessage,
        closeConnection,
        closeAllConnections,
        send
    };
})();

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    WebSocketService.init();
});

// WebSocketService를 window 객체에 추가
window.WebSocketService = WebSocketService; 