// 카드 컴포넌트 관리 모듈
const CardComponent = (function() {
    // 변화율 정보를 저장할 객체
    const priceChangeCache = {};

    // 모의 거래 데이터를 저장할 객체
    const mockTradingData = {};
    
    // 모의 거래 데이터 초기화 또는 가져오기 함수
    function getMockTradingData(card) {
        const cardId = card.getAttribute("data-card-id") || card.id;
        
        // 모의 거래 데이터가 없으면 초기화
        if (!mockTradingData[cardId]) {
            mockTradingData[cardId] = {
                currentPrice: 0,
                signalStrength: 0,
                lastSignalTime: 0
            };
        }
        
        return mockTradingData[cardId];
    }

    // 카드 생성 함수
    function createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair) {
        console.log('카드 생성:', exchange, currencyPair, symbol, quoteCurrency, displayPair);
        
        // 기본 ID (하위 호환성 유지)
        const baseId = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 짧은 ID 생성 (8자)
        const shortId = FormatUtils.generateShortId();
        const cardId = `${baseId}-${shortId}`;
        
        // 카드 생성 시간
        const createdAt = new Date();
        const createdAtFormatted = FormatUtils.formatDateTime(createdAt);
        
        const card = document.createElement('div');
        card.className = 'analysis-card';
        card.id = cardId;
        
        // 데이터 속성 추가
        card.setAttribute('data-exchange', exchange);
        card.setAttribute('data-currency-pair', currencyPair);
        card.setAttribute('data-symbol', symbol);
        card.setAttribute('data-quote-currency', quoteCurrency);
        card.setAttribute('data-base-id', baseId);
        card.setAttribute('data-short-id', shortId);
        card.setAttribute('data-created-at', createdAt.toISOString());
        
        console.log('카드 ID:', cardId, '기본 ID:', baseId, '짧은 ID:', shortId);
        
        // 카드 내용 생성
        card.innerHTML = `
            <div class="card-header">
                <div class="exchange-pair">${exchange} - ${displayPair}</div>
                <div class="card-actions">
                    <button class="start-button">시작</button>
                    <button class="stop-button" style="display: none;">중지</button>
                    <button class="retry-button" style="display: none;">재시도</button>
                    <button class="delete-button">삭제</button>
                </div>
            </div>
            <div class="card-subheader">
                <div class="card-created-time">생성: ${createdAtFormatted}</div>
                <div class="card-id-display">ID: ${baseId}-${shortId}</div>
            </div>
            <div class="loading-indicator" style="display: none;">
                <div class="spinner"></div>
                <span>분석 데이터 로딩 중...</span>
            </div>
            <div class="card-content">
                <!-- 첫 번째 행 - 가로로 모든 요소 배치 -->
                <div class="info-row">
                    <!-- 왼쪽 정보 블록 (가격 + 변화율) -->
                    <div class="info-block price-block">
                        <div class="current-price">-</div>
                        <div class="price-change neutral">0.0%</div>
                    </div>
                    
                    <!-- 분석 정보 블록 -->
                    <div class="info-column">
                        <div class="result-row">
                            <div class="result-label">분석결과:</div>
                            <div class="result-value neutral">HOLD</div>
                        </div>
                        
                        <div class="signal-row">
                            <div class="signal-label">매수신호:</div>
                            <div class="signal-value">0.0%</div>
                        </div>
                        <div class="signal-strength-container">
                            <div class="signal-strength-bar" style="width: 0%;"></div>
                        </div>
                    </div>
                    
                    <!-- 지표 정보 블록 -->
                    <div class="indicators-block">
                        <div class="indicator-row">
                            <div class="indicator-label">MA:</div>
                            <div class="indicator-value sma-signal neutral">대기중</div>
                        </div>
                        <div class="indicator-row">
                            <div class="indicator-label">RSI:</div>
                            <div class="indicator-value rsi-value neutral">-</div>
                        </div>
                        <div class="indicator-row">
                            <div class="indicator-label">BB:</div>
                            <div class="indicator-value bb-signal neutral">대기중</div>
                        </div>
                        <div class="indicator-row">
                            <div class="indicator-label">Vol:</div>
                            <div class="indicator-value volume-signal neutral">-</div>
                        </div>
                    </div>
                    
                    <!-- 시장 상태 블록 -->
                    <div class="market-block">
                        <div class="market-label">시장상태:</div>
                        <div class="market-condition neutral">중립 상태</div>
                    </div>
                </div>
                
                <!-- 트레이딩 스타일 정보 -->
                <div class="trading-style-info">
                    <div class="analysis-message">트레이딩 스타일: 단타, 매수 신호 강도: 0.0%, 중립 상태</div>
                </div>
            </div>
        `;
        
        // 이벤트 설정
        setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency);
        
        return card;
    }
    
    // 카드 이벤트 설정 함수
    function setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency) {
        const cardId = card.getAttribute('data-card-id') || card.id;
        console.log('카드 이벤트 설정:', cardId);
        
        // 시작 버튼 이벤트
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const retryBtn = card.querySelector('.retry-button');
        const deleteBtn = card.querySelector('.delete-button');
        
        if (startBtn) {
            startBtn.addEventListener('click', function() {
                startAnalysis(card, exchange, currencyPair, symbol, quoteCurrency);
            });
        }
        
        if (stopBtn) {
            stopBtn.addEventListener('click', function() {
                stopAnalysis(card);
            });
        }
        
        if (retryBtn) {
            retryBtn.addEventListener('click', function() {
                retryAnalysis(card, exchange, currencyPair, symbol, quoteCurrency);
            });
        }
        
        if (deleteBtn) {
            deleteBtn.addEventListener('click', function() {
                deleteCard(card);
            });
        }
    }
    
    // 분석 시작 함수
    function startAnalysis(card, exchange, currencyPair, symbol, quoteCurrency) {
        console.log('분석 시작:', exchange, currencyPair);
        
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const retryBtn = card.querySelector('.retry-button');
        const loadingIndicator = card.querySelector('.loading-indicator');
        
        if (startBtn && stopBtn && loadingIndicator) {
            startBtn.style.display = 'none';
            stopBtn.style.display = 'inline-block';
            if (retryBtn) retryBtn.style.display = 'none';
            loadingIndicator.style.display = 'flex';
        }
        
        // 웹소켓 서비스를 통해 분석 시작
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
        
        // 메시지 콜백 등록 (중요: 카드 ID가 변경되더라도 업데이트 가능하도록)
        WebSocketService.onMessage(card.id, function(data) {
            console.log(`[${card.id}] 메시지 콜백 실행:`, data);
            updateCard(card, data);
        });
    }
    
    // 분석 중지 함수
    function stopAnalysis(card) {
        console.log('분석 중지');
        
        const exchange = card.getAttribute('data-exchange');
        const currencyPair = card.getAttribute('data-currency-pair');
        
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const loadingIndicator = card.querySelector('.loading-indicator');
        
        if (startBtn && stopBtn && loadingIndicator) {
            startBtn.style.display = 'inline-block';
            stopBtn.style.display = 'none';
            loadingIndicator.style.display = 'none';
        }
        
        // 웹소켓 연결 중지
        const request = {
            action: 'stopAnalysis',
            exchange: exchange,
            currencyPair: currencyPair
        };
        
        console.log('웹소켓 요청 전송:', request);
        window.WebSocketService.send(request);
    }
    
    // 분석 재시도 함수
    function retryAnalysis(card, exchange, currencyPair, symbol, quoteCurrency) {
        console.log('분석 재시도');
        
        const startBtn = card.querySelector('.start-button');
        const retryBtn = card.querySelector('.retry-button');
        
        if (startBtn && retryBtn) {
            retryBtn.style.display = 'none';
            // 분석 시작 함수 호출
            startAnalysis(card, exchange, currencyPair, symbol, quoteCurrency);
        }
    }
    
    // 카드 삭제 함수
    function deleteCard(card) {
        console.log('카드 삭제');
        
        const exchange = card.getAttribute('data-exchange');
        const currencyPair = card.getAttribute('data-currency-pair');
        
        // 웹소켓 연결 중지
        const request = {
            action: 'stopAnalysis',
            exchange: exchange,
            currencyPair: currencyPair
        };
        
        console.log('웹소켓 요청 전송:', request);
        window.WebSocketService.send(request);
        
        // 카드 제거
        card.remove();
    }
    
    // 카드 업데이트 함수
    function updateCard(card, data) {
        console.log(`[${card.id}] 카드 업데이트 데이터:`, data);
        
        try {
            // 로딩 표시 숨기기
            const loadingIndicator = card.querySelector('.loading-indicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
            
            // 백엔드에서 받은 카드 ID 확인 및 업데이트
            if (data.cardId && data.cardId !== card.id) {
                console.log(`카드 ID 업데이트: ${card.id} -> ${data.cardId}`);
                
                // 카드 ID 업데이트
                const oldId = card.id;
                card.id = data.cardId;
                
                // ID 표시 업데이트
                const cardIdDisplay = card.querySelector('.card-id-display');
                if (cardIdDisplay) {
                    cardIdDisplay.textContent = `ID: ${data.cardId}`;
                }
                
                // 상태 객체 업데이트 (window.state.activeCards)
                if (window.state && window.state.activeCards) {
                    if (window.state.activeCards[oldId]) {
                        window.state.activeCards[data.cardId] = window.state.activeCards[oldId];
                        window.state.activeCards[data.cardId].card = card;
                        delete window.state.activeCards[oldId];
                        console.log(`상태 객체 업데이트 완료: ${oldId} -> ${data.cardId}`);
                    }
                }
                
                // 메시지 콜백 재등록 (ID 변경 후에도 콜백이 작동하도록)
                if (window.WebSocketService && window.WebSocketService.onMessage) {
                    window.WebSocketService.onMessage(data.cardId, function(newData) {
                        console.log(`[${data.cardId}] 새 메시지 콜백 실행:`, newData);
                        updateCard(card, newData);
                    });
                }
            }
            
            // 여기서 실제 UI 업데이트 로직 실행
            updateCardUI(card, data);
            
        } catch (error) {
            console.error(`[${card.id}] 카드 업데이트 중 오류:`, error);
            showError(card, '데이터 처리 중 오류가 발생했습니다.');
        }
    }
    
    // 웹소켓 메시지 처리 함수
    function processWebSocketMessage(data) {
        console.log('WebSocket 메시지 수신:', data);
        
        // 에러 메시지 처리
        if (data.error) {
            console.error('WebSocket 에러:', data.error);
            return;
        }
        
        // cardId 확인 (새로운 응답 형식)
        if (data.cardId) {
            console.log('응답에 cardId 포함됨:', data.cardId);
            const card = document.getElementById(data.cardId);
            if (card) {
                console.log('cardId로 카드 찾음:', data.cardId);
                updateCard(card, data);
                return;
                } else {
                console.warn('cardId에 해당하는 카드를 찾을 수 없음:', data.cardId);
            }
        }
        
        // 기존 로직 - exchange와 currencyPair로 카드 찾기
        const exchange = data.exchange;
        const currencyPair = data.currencyPair;
        
        if (!exchange || !currencyPair) {
            console.error('메시지에 필수 정보가 없습니다:', data);
            return;
        }
        
        // 기존 방식으로 카드 찾기
        const baseId = `${exchange}-${currencyPair}`.toLowerCase();
        const card = document.querySelector(`[data-base-id="${baseId}"]`);
        
        if (card) {
            console.log('baseId로 카드 찾음:', baseId);
            updateCard(card, data);
                } else {
            console.warn(`카드를 찾을 수 없음: ${baseId}`, data);
        }
    }
    
    // UI 업데이트 함수
    function updateCardUI(card, data) {
        if (!card || !data) {
            console.error('유효하지 않은 카드 또는 데이터:', card, data);
            return;
        }

        const cardId = card.id;
        console.log(`[${cardId}] 카드 UI 업데이트 시작`, data);
        
        // 분석 데이터 가져오기
        const analysisData = getMockTradingData(card);

        // 로딩 표시 숨기기
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'none';
        }

        // 가격 정보 업데이트
        const priceValue = card.querySelector('.current-price');
        if (priceValue && data.currentPrice !== undefined) {
            priceValue.textContent = FormatUtils.formatPrice(data.currentPrice);
            console.log(`[${cardId}] 가격 업데이트: ${data.currentPrice}`);
        }

        // 가격 변화 업데이트
        const priceChange = card.querySelector('.price-change');
        if (priceChange && data.priceChangePercent !== undefined) {
            const changeValue = parseFloat(data.priceChangePercent);
            // 값이 0이더라도 표시하고, 소수점 두 자리까지 표시
            priceChange.textContent = changeValue.toFixed(2) + '%';
            priceChange.className = 'price-change ' + (changeValue > 0 ? 'positive' : changeValue < 0 ? 'negative' : 'neutral');
            console.log(`[${cardId}] 가격 변화 업데이트: ${changeValue.toFixed(2)}%`);
        }

        // 분석 결과 업데이트
        const resultValue = card.querySelector('.result-value');
        if (resultValue && data.analysisResult) {
            // 반등 관련 결과를 매수/매도 신호로 변환
            const displayResult = translateAnalysisResult(data.analysisResult);
            resultValue.textContent = displayResult;
            resultValue.className = 'result-value ' + 
                (displayResult === 'BUY' || displayResult === 'STRONG_BUY' ? 'positive' : 
                 displayResult === 'SELL' || displayResult === 'STRONG_SELL' ? 'negative' : 'neutral');
            console.log(`[${cardId}] 분석 결과 업데이트: ${data.analysisResult} -> ${displayResult}`);
        }

        // 매수 신호 강도 업데이트 (signal-value 요소)
        const signalValue = card.querySelector('.signal-value');
        if (signalValue && data.buySignalStrength !== undefined) {
            const buySignalStrength = parseFloat(data.buySignalStrength);
            signalValue.textContent = buySignalStrength.toFixed(1) + '%';
            
            // 신호 강도에 따른 클래스 설정
            let signalClass = 'neutral';
            if (buySignalStrength >= 70) {
                signalClass = 'strong-positive';
            } else if (buySignalStrength >= 50) {
                signalClass = 'positive';
            } else if (buySignalStrength >= 30) {
                signalClass = 'weak-positive';
            }
            
            signalValue.className = 'signal-value ' + signalClass;
            console.log(`[${cardId}] 매수 신호 강도 업데이트 (signal-value): ${buySignalStrength}%`);
        }

        // 신호 업데이트 (SMA, RSI, 볼린저 밴드 등)
        updateSignals(card, data);

        // 시장 상태 업데이트
        const marketCondition = card.querySelector('.market-condition');
        if (marketCondition && data.marketCondition) {
            const condition = data.marketCondition;
            const smaSignal = data.smaSignal || '';
            
            // 시장 상태와 SMA 신호를 함께 고려하여 표시
            const displayCondition = getDisplayMarketCondition(condition, smaSignal);
            marketCondition.textContent = displayCondition;
            
            // 클래스 설정
            let conditionClass = 'neutral';
            if (condition === 'OVERBOUGHT' || condition === 'STRONG_SELL' || condition === 'SELL') {
                conditionClass = 'negative';
            } else if (condition === 'OVERSOLD' || condition === 'STRONG_BUY' || condition === 'BUY' || 
                      smaSignal === 'STRONG_UPTREND' || smaSignal === 'BULLISH') {
                conditionClass = 'positive';
            }
            
            marketCondition.className = 'market-condition ' + conditionClass;
            console.log(`[${cardId}] 시장 상태 업데이트: ${condition} + ${smaSignal} -> ${displayCondition}`);
        }

        // 메시지 업데이트
        const messageElement = card.querySelector('.analysis-message');
        if (messageElement && data.message) {
            messageElement.textContent = data.message;
            console.log(`[${cardId}] 메시지 업데이트: ${data.message}`);
        }

        // 신호 강도 업데이트
        const signalStrengthBar = card.querySelector('.signal-strength-bar');
        if (signalStrengthBar) {
            // buySignalStrength 값이 있으면 사용, 없으면 signalStrength 사용
            let strength = data.buySignalStrength !== undefined ? parseFloat(data.buySignalStrength) : (analysisData.signalStrength || 0);
            
            console.log(`[${cardId}] 신호 강도 업데이트: ${strength}%`);
            
            if (strength > 0 || strength === 0) { // 0도 표시
                signalStrengthBar.style.width = Math.min(strength, 100) + '%';
                
                // 신호 강도에 따른 색상 설정
                if (strength >= 70) {
                    signalStrengthBar.style.backgroundColor = 'var(--strong-buy-color, #00b894)';
                } else if (strength >= 50) {
                    signalStrengthBar.style.backgroundColor = 'var(--buy-color, #00cec9)';
                } else if (strength >= 30) {
                    signalStrengthBar.style.backgroundColor = 'var(--weak-buy-color, #74b9ff)';
                } else {
                    signalStrengthBar.style.backgroundColor = 'var(--neutral-color, #a0a0a0)';
                }
            }
        }
        
        console.log(`[${cardId}] 카드 UI 업데이트 완료`);
    }
    
    // 신호 업데이트 함수
    function updateSignals(card, data) {
        if (!card || !data) return;

        const cardId = card.id;
        console.log(`[${cardId}] 신호 업데이트 시작`, data);
        
        // SMA 신호 업데이트
        const smaSignal = card.querySelector('.sma-signal');
        if (smaSignal && data.smaSignal) {
            const signalValue = data.smaSignal;
            smaSignal.textContent = signalValue;
            smaSignal.className = 'indicator-value sma-signal ' + 
                (signalValue === 'BULLISH' || signalValue === 'MODERATELY_BULLISH' || signalValue === 'SLIGHTLY_BULLISH' || signalValue === 'STRONG_UPTREND' ? 'positive' : 
                 signalValue === 'BEARISH' || signalValue === 'MODERATELY_BEARISH' || signalValue === 'SLIGHTLY_BEARISH' || signalValue === 'STRONG_DOWNTREND' ? 'negative' : 'neutral');
            console.log(`[${cardId}] SMA 신호 업데이트: ${signalValue}`);
        }
        
        // RSI 값 업데이트
        const rsiValue = card.querySelector('.rsi-value');
        if (rsiValue && data.rsiValue !== undefined) {
            const rsiVal = parseFloat(data.rsiValue);
            rsiValue.textContent = rsiVal.toFixed(2);
            rsiValue.className = 'indicator-value rsi-value ' + 
                (data.rsiSignal === 'OVERSOLD' ? 'positive' : 
                 data.rsiSignal === 'OVERBOUGHT' ? 'negative' : 'neutral');
            console.log(`[${cardId}] RSI 값 업데이트: ${rsiVal}`);
        }
        
        // 볼린저 밴드 신호 업데이트
        const bbSignal = card.querySelector('.bb-signal');
        if (bbSignal && data.bollingerSignal) {
            const signalValue = data.bollingerSignal;
            bbSignal.textContent = signalValue;
            bbSignal.className = 'indicator-value bb-signal ' + 
                (signalValue === 'LOWER_TOUCH' ? 'positive' : 
                 signalValue === 'UPPER_TOUCH' ? 'negative' : 'neutral');
            console.log(`[${cardId}] 볼린저 밴드 신호 업데이트: ${signalValue}`);
        }
        
        // 거래량 변화 업데이트
        const volumeSignal = card.querySelector('.volume-signal');
        if (volumeSignal && data.volumeChangePercent !== undefined) {
            const volumeChange = parseFloat(data.volumeChangePercent);
            // 값이 0이더라도 표시하고, 소수점 두 자리까지 표시
            volumeSignal.textContent = volumeChange.toFixed(2) + '%';
            
            let volumeClass = 'neutral';
            if (volumeChange > 20) {
                volumeClass = 'positive';
            } else if (volumeChange < -20) {
                volumeClass = 'negative';
            }
            
            volumeSignal.className = 'indicator-value volume-signal ' + volumeClass;
            console.log(`[${cardId}] 거래량 변화 업데이트: ${volumeChange.toFixed(2)}%`);
        }
        
        console.log(`[${cardId}] 신호 업데이트 완료`);
    }
    
    // 시장 상태 번역
    function translateMarketCondition(signal) {
        switch(signal) {
            case 'OVERBOUGHT':
                return '과매수';
            case 'OVERSOLD':
                return '과매도';
            case 'BULLISH':
                return '상승세';
            case 'BEARISH':
                return '하락세';
            case 'NEUTRAL':
                return '중립';
            default:
                return signal;
        }
    }
    
    // 표시용 시장 상태 결정
    function getDisplayMarketCondition(condition, smaSignal) {
        // 기본 상태 번역
        const baseCondition = translateMarketCondition(condition);
        
        // SMA 신호가 없으면 기본 상태만 반환
        if (!smaSignal) {
            return baseCondition;
        }
        
        // 과매수/과매도 상태가 있으면 우선 표시
        if (condition === 'OVERBOUGHT' || condition === 'OVERSOLD') {
            // SMA 신호가 상승/하락과 일치하면 함께 표시
            if ((condition === 'OVERBOUGHT' && (smaSignal === 'STRONG_UPTREND' || smaSignal === 'BULLISH')) ||
                (condition === 'OVERSOLD' && (smaSignal === 'STRONG_DOWNTREND' || smaSignal === 'BEARISH'))) {
                return baseCondition;
            }
            // 불일치하는 경우 과매수/과매도만 표시
            return baseCondition;
        }
        
        // 중립 상태일 때는 SMA 신호 기반으로 표시
        if (condition === 'NEUTRAL') {
            if (smaSignal === 'STRONG_UPTREND' || smaSignal === 'BULLISH') {
                return '상승세';
            } else if (smaSignal === 'STRONG_DOWNTREND' || smaSignal === 'BEARISH') {
                return '하락세';
            }
        }
        
        // 기본적으로 시장 상태 반환
        return baseCondition;
    }
    
    // 분석 결과 번역 (반등 관련 결과를 매수/매도 신호로 변환)
    function translateAnalysisResult(result) {
        switch(result) {
            case 'VERY_STRONG_REBOUND':
            case 'STRONG_REBOUND':
                return 'STRONG_BUY';
            case 'POSSIBLE_REBOUND':
            case 'WEAK_REBOUND':
                return 'BUY';
            case 'VERY_WEAK_REBOUND':
                return 'NEUTRAL';
            case 'NO_REBOUND':
                // 추가 정보를 기반으로 결정
                // 현재는 단순히 NEUTRAL 반환
                return 'NEUTRAL';
            // 기존 매수/매도 신호는 그대로 유지
            case 'STRONG_BUY':
            case 'BUY':
            case 'NEUTRAL':
            case 'SELL':
            case 'STRONG_SELL':
                return result;
            default:
                return 'NEUTRAL';
        }
    }
    
    // 공개 API
    return {
        createCard,
        updateCard,
        deleteCard,
        processWebSocketMessage,
        updateCardUI
    };
})(); 

// 전역 객체에 CardComponent 할당
window.CardComponent = CardComponent; 