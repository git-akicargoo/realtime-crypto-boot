// 카드 컴포넌트 관리 모듈
const CardComponent = (function() {
    // 카드 생성 함수
    function createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair) {
        console.log('카드 생성:', exchange, currencyPair, symbol, quoteCurrency, displayPair);
        
        const card = document.createElement('div');
        card.className = 'analysis-card';
        card.id = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 카드 내용 설정
        card.innerHTML = `
            <div class="card-header">
                <h3>${exchange} - ${displayPair}</h3>
                <div class="card-actions">
                    <button class="start-button">시작</button>
                    <button class="stop-button" style="display: none;">중지</button>
                    <button class="retry-button" style="display: none;">재시도</button>
                    <button class="delete-button">삭제</button>
                </div>
            </div>
            <div class="loading-indicator" style="display: none;">
                <div class="spinner"></div>
                <span>분석 데이터 로딩 중...</span>
            </div>
            <div class="card-content">
                <div class="price-section">
                    <div class="current-price">-</div>
                    <div class="price-change neutral">-</div>
                </div>
                <div class="indicators-section">
                    <div class="indicator-item">
                        <div class="indicator-label">이동평균선:</div>
                        <div class="indicator-value sma-signal neutral">대기중</div>
                    </div>
                    <div class="indicator-item">
                        <div class="indicator-label">RSI:</div>
                        <div class="indicator-value rsi-value neutral">-</div>
                    </div>
                    <div class="indicator-item">
                        <div class="indicator-label">볼린저밴드:</div>
                        <div class="indicator-value bb-signal neutral">대기중</div>
                    </div>
                    <div class="indicator-item">
                        <div class="indicator-label">거래량변화:</div>
                        <div class="indicator-value volume-signal neutral">-</div>
                    </div>
                </div>
                <div class="market-section">
                    <div class="market-label">시장상태:</div>
                    <div class="market-condition neutral">대기중</div>
                </div>
                <div class="signal-section">
                    <div class="signal-strength-label">매수신호강도:</div>
                    <div class="signal-strength-value">0.0%</div>
                    <div class="signal-strength-container">
                        <div class="signal-strength-bar weak-buy" style="width: 0%;"></div>
                    </div>
                </div>
                <div class="result-section">
                    <div class="result-label">분석결과:</div>
                    <div class="result-value neutral">대기중</div>
                </div>
                <div class="message-section">
                    <div class="analysis-message">분석이 시작되면 여기에 결과가 표시됩니다.</div>
                </div>
            </div>
        `;
        
        // 이벤트 리스너 설정
        setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency);
        
        return card;
    }
    
    // 카드 이벤트 설정
    function setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency) {
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const retryBtn = card.querySelector('.retry-button');
        const deleteBtn = card.querySelector('.delete-button');
        
        if (startBtn) {
            startBtn.addEventListener('click', function() {
                WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
            });
        }
        
        if (stopBtn) {
            stopBtn.addEventListener('click', function() {
                WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
            });
        }
        
        if (retryBtn) {
            retryBtn.addEventListener('click', function() {
                retryBtn.style.display = 'none';
                WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
            });
        }
        
        if (deleteBtn) {
            deleteBtn.addEventListener('click', function() {
                deleteCard(exchange, currencyPair, symbol, quoteCurrency, card);
            });
        }
    }
    
    // 카드 삭제
    function deleteCard(exchange, currencyPair, symbol, quoteCurrency, card) {
        // 웹소켓 연결 종료
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
        
        // 카드 요소 제거
        if (card && card.parentElement) {
            card.parentElement.removeChild(card);
        }
        
        // 상태에서 제거
        const cardId = `${exchange}-${currencyPair}`.toLowerCase();
        if (window.state && window.state.activeCards) {
            delete window.state.activeCards[cardId];
        }
    }
    
    // 카드 데이터 업데이트
    function updateCard(card, data) {
        console.log('카드 업데이트 시작 - 데이터:', data);
        
        try {
            // 로딩 표시 숨기기
            const loadingIndicator = card.querySelector('.loading-indicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
            
            // 시작 버튼 숨기고 중지 버튼 표시
            const startButton = card.querySelector('.start-button');
            const stopButton = card.querySelector('.stop-button');
            const retryButton = card.querySelector('.retry-button');
            
            if (startButton && stopButton) {
                startButton.style.display = 'none';
                stopButton.style.display = 'inline-block';
                if (retryButton) retryButton.style.display = 'none';
            }
            
            // 가격 정보 업데이트
            updatePriceInfo(card, data);
            
            // 기술 지표 업데이트
            updateIndicators(card, data);
            
            // 시장 상태 업데이트
            updateMarketCondition(card, data);
            
            // 매수 신호 강도 업데이트
            updateSignalStrength(card, data);
            
            // 분석 결과 업데이트
            updateAnalysisResult(card, data);
            
            console.log('카드 업데이트 완료');
        } catch (error) {
            console.error('카드 업데이트 중 오류:', error);
        }
    }
    
    // 가격 정보 업데이트
    function updatePriceInfo(card, data) {
        // 현재 가격
        const priceValue = card.querySelector('.current-price');
        if (priceValue && data.currentPrice !== undefined) {
            priceValue.textContent = Formatters.formatPrice(data.currentPrice);
        }
        
        // 가격 변화
        const priceChange = card.querySelector('.price-change');
        if (priceChange && data.priceChangePercent !== undefined) {
            const changeValue = data.priceChangePercent;
            priceChange.textContent = Formatters.formatPercent(changeValue);
            priceChange.className = 'price-change ' + (changeValue > 0 ? 'positive' : changeValue < 0 ? 'negative' : 'neutral');
        }
    }
    
    // 기술 지표 업데이트 (SMA, RSI, 볼린저, 거래량)
    function updateIndicators(card, data) {
        // SMA 신호
        const smaSignal = card.querySelector('.sma-signal');
        if (smaSignal) {
            const signal = data.smaSignal || 'NEUTRAL';
            smaSignal.textContent = getSignalText(signal);
            smaSignal.className = 'indicator-value sma-signal ' + getSignalClass(signal);
        }
        
        // RSI
        const rsiValue = card.querySelector('.rsi-value');
        if (rsiValue) {
            const rsi = data.rsiValue || 0;
            const rsiSignal = data.rsiSignal || 'NEUTRAL';
            rsiValue.textContent = `${rsi.toFixed(0)} (${getSignalText(rsiSignal)})`;
            rsiValue.className = 'indicator-value rsi-value ' + getSignalClass(rsiSignal);
        }
        
        // 볼린저 밴드
        const bbSignal = card.querySelector('.bb-signal');
        if (bbSignal) {
            const signal = data.bollingerSignal || 'INSIDE';
            bbSignal.textContent = getBollingerText(signal);
            bbSignal.className = 'indicator-value bb-signal ' + getBollingerClass(signal);
        }
        
        // 거래량 변화
        const volumeSignal = card.querySelector('.volume-signal');
        if (volumeSignal && data.volumeChangePercent !== undefined) {
            volumeSignal.textContent = Formatters.formatPercent(data.volumeChangePercent);
            volumeSignal.className = 'indicator-value volume-signal ' + 
                (data.volumeChangePercent > 30 ? 'positive' : 
                 data.volumeChangePercent < -30 ? 'negative' : 'neutral');
        }
    }
    
    // 시장 상태 업데이트
    function updateMarketCondition(card, data) {
        const marketCondition = card.querySelector('.market-condition');
        if (marketCondition) {
            const condition = data.marketCondition || 'NEUTRAL';
            marketCondition.textContent = getMarketConditionText(condition);
            marketCondition.className = 'market-condition ' + getSignalClass(condition);
        }
    }
    
    // 매수 신호 강도 업데이트
    function updateSignalStrength(card, data) {
        const signalStrengthValue = card.querySelector('.signal-strength-value');
        const signalStrengthBar = card.querySelector('.signal-strength-bar');
        
        if (signalStrengthValue && signalStrengthBar) {
            let strength = 0;
            
            // 직접적인 buySignalStrength 값 확인
            if (data.buySignalStrength !== undefined && data.buySignalStrength !== null) {
                strength = data.buySignalStrength;
            } 
            // 메시지에서 강도 추출 시도
            else if (data.message) {
                const match = data.message.match(/매수 신호 강도: (\d+\.?\d*)%/);
                if (match && match[1]) {
                    strength = parseFloat(match[1]);
                }
            }
            
            // 값 업데이트
            signalStrengthValue.textContent = Formatters.formatPercent(strength);
            signalStrengthBar.style.width = `${strength}%`;
            
            // 색상 클래스 설정
            if (strength >= 70) {
                signalStrengthBar.className = 'signal-strength-bar strong-buy';
            } else if (strength >= 50) {
                signalStrengthBar.className = 'signal-strength-bar moderate-buy';
            } else {
                signalStrengthBar.className = 'signal-strength-bar weak-buy';
            }
        }
    }
    
    // 분석 결과 업데이트
    function updateAnalysisResult(card, data) {
        // 분석 결과
        const resultValue = card.querySelector('.result-value');
        if (resultValue) {
            const result = data.analysisResult || 'NEUTRAL';
            resultValue.textContent = result;
            resultValue.className = 'result-value ' + getSignalClass(result);
        }
        
        // 메시지
        const messageElement = card.querySelector('.analysis-message');
        if (messageElement && data.message) {
            messageElement.textContent = data.message;
        }
    }
    
    // 신호 텍스트 변환
    function getSignalText(signal) {
        switch(signal) {
            case 'BUY': return '매수';
            case 'SELL': return '매도';
            case 'BULLISH': return '상승';
            case 'BEARISH': return '하락';
            case 'NEUTRAL': return '중립';
            case 'OVERBOUGHT': return '과매수';
            case 'OVERSOLD': return '과매도';
            default: return signal || '중립';
        }
    }
    
    // 신호 클래스 반환
    function getSignalClass(signal) {
        switch(signal) {
            case 'BUY':
            case 'BULLISH':
            case 'OVERSOLD':
                return 'positive';
            case 'SELL':
            case 'BEARISH':
            case 'OVERBOUGHT':
                return 'negative';
            default:
                return 'neutral';
        }
    }
    
    // 볼린저 텍스트 변환
    function getBollingerText(signal) {
        switch(signal) {
            case 'UPPER_TOUCH': return '상단 접근';
            case 'LOWER_TOUCH': return '하단 접근';
            case 'UPPER_BREAK': return '상단 돌파';
            case 'LOWER_BREAK': return '하단 돌파';
            case 'INSIDE': return '밴드 내부';
            default: return signal || '대기중';
        }
    }
    
    // 볼린저 클래스 반환
    function getBollingerClass(signal) {
        switch(signal) {
            case 'UPPER_BREAK':
                return 'positive';
            case 'LOWER_BREAK':
                return 'negative';
            case 'UPPER_TOUCH':
                return 'warning positive';
            case 'LOWER_TOUCH':
                return 'warning negative';
            default:
                return 'neutral';
        }
    }
    
    // 시장 상태 텍스트 변환
    function getMarketConditionText(condition) {
        switch(condition) {
            case 'BULLISH': return '상승 추세';
            case 'BEARISH': return '하락 추세';
            case 'NEUTRAL': return '중립 추세';
            default: return condition || '대기중';
        }
    }
    
    // 오류 표시
    function showError(card, errorMessage) {
        // 로딩 표시 숨기기
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'none';
        }
        
        // 버튼 상태 변경
        const startButton = card.querySelector('.start-button');
        const stopButton = card.querySelector('.stop-button');
        const retryButton = card.querySelector('.retry-button');
        
        if (startButton && stopButton && retryButton) {
            startButton.style.display = 'none';
            stopButton.style.display = 'none';
            retryButton.style.display = 'inline-block';
        }
        
        // 오류 메시지 표시
        const messageElement = card.querySelector('.analysis-message');
        if (messageElement) {
            messageElement.textContent = errorMessage || '분석 중 오류가 발생했습니다.';
            messageElement.style.color = 'var(--negative-color)';
        }
        
        // 분석 결과 업데이트
        const resultValue = card.querySelector('.result-value');
        if (resultValue) {
            resultValue.textContent = 'ERROR';
            resultValue.className = 'result-value negative';
        }
    }
    
    // 공개 API
    return {
        createCard,
        updateCard,
        showError,
        deleteCard
    };
})(); 