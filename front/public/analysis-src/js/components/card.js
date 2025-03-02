// 카드 컴포넌트 관리 모듈
const CardComponent = (function() {
    // 카드 생성 함수
    function createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair) {
        console.log('카드 생성:', exchange, currencyPair, symbol, quoteCurrency, displayPair);
        
        const card = document.createElement('div');
        card.className = 'analysis-card';
        card.id = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 카드 내용을 가로로 배치하도록 수정
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
                            <div class="indicator-value">Vol:</div>
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
        // 웹소켓 연결 종료 - card를 전달하지 않음
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency);
        
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
    
    // 카드 업데이트 함수 개선
    function updateCard(card, data) {
        if (!card || !data) {
            console.error('카드 업데이트 실패: 유효하지 않은 데이터', data);
            return;
        }
        
        console.log('카드 업데이트:', data);
        
        try {
            // 로딩 표시 숨기기
            const loadingIndicator = card.querySelector('.loading-indicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
            
            // 가격 업데이트
            const priceElement = card.querySelector('.current-price');
            if (priceElement && data.currentPrice) {
                priceElement.textContent = FormatUtils.formatPrice(data.currentPrice);
            }
            
            // 가격 변화 업데이트
            const priceChangeElement = card.querySelector('.price-change');
            if (priceChangeElement && data.priceChangePercent !== undefined) {
                const changePercent = data.priceChangePercent;
                const formattedChange = FormatUtils.formatPercent(changePercent);
                priceChangeElement.textContent = formattedChange;
                
                // 클래스 업데이트
                priceChangeElement.className = 'price-change';
                if (changePercent > 0) {
                    priceChangeElement.classList.add('positive');
                } else if (changePercent < 0) {
                    priceChangeElement.classList.add('negative');
                } else {
                    priceChangeElement.classList.add('neutral');
                }
            }
            
            // SMA 신호 업데이트
            const smaElement = card.querySelector('.sma-signal');
            if (smaElement) {
                let smaText = '대기중';
                let smaClass = 'neutral';
                
                if (data.smaSignal) {
                    smaText = translateSignal(data.smaSignal);
                    if (data.smaSignal === 'BULLISH') {
                        smaClass = 'positive';
                    } else if (data.smaSignal === 'BEARISH') {
                        smaClass = 'negative';
                    }
                } else if (data.sma1Difference || data.smaShortDifference) {
                    // smaSignal이 없지만 sma1Difference가 있을 경우
                    const diff = data.sma1Difference || data.smaShortDifference;
                    if (diff < -0.1) {
                        smaText = '강한 하락';
                        smaClass = 'negative';
                    } else if (diff < 0) {
                        smaText = '약한 하락';
                        smaClass = 'negative';
                    } else if (diff > 0.1) {
                        smaText = '강한 상승';
                        smaClass = 'positive';
                    } else if (diff > 0) {
                        smaText = '약한 상승';
                        smaClass = 'positive';
                    } else {
                        smaText = '중립';
                    }
                }
                
                smaElement.textContent = smaText;
                smaElement.className = 'indicator-value sma-signal ' + smaClass;
            }
            
            // RSI 값 업데이트
            const rsiElement = card.querySelector('.rsi-value');
            if (rsiElement && data.rsiValue !== undefined) {
                const rsiValue = data.rsiValue;
                rsiElement.textContent = rsiValue.toFixed(1);
                
                // 클래스 업데이트
                rsiElement.className = 'indicator-value rsi-value';
                if (rsiValue > 70) {
                    rsiElement.classList.add('overbought');
                } else if (rsiValue < 30) {
                    rsiElement.classList.add('oversold');
                } else {
                    rsiElement.classList.add('neutral');
                }
            }
            
            // 볼린저 밴드 신호 업데이트
            const bbElement = card.querySelector('.bb-signal');
            if (bbElement && data.bollingerSignal) {
                bbElement.textContent = translateSignal(data.bollingerSignal);
                
                // 클래스 업데이트
                bbElement.className = 'indicator-value bb-signal';
                if (data.bollingerSignal.includes('UPPER')) {
                    bbElement.classList.add('overbought');
                } else if (data.bollingerSignal.includes('LOWER')) {
                    bbElement.classList.add('oversold');
                } else {
                    bbElement.classList.add('neutral');
                }
            }
            
            // 거래량 변화 업데이트
            const volumeElement = card.querySelector('.volume-signal');
            if (volumeElement && data.volumeChangePercent !== undefined) {
                volumeElement.textContent = FormatUtils.formatPercent(data.volumeChangePercent);
                
                // 클래스 업데이트
                volumeElement.className = 'indicator-value volume-signal';
                if (data.volumeChangePercent > 20) {
                    volumeElement.classList.add('positive');
                } else if (data.volumeChangePercent < -20) {
                    volumeElement.classList.add('negative');
                } else {
                    volumeElement.classList.add('neutral');
                }
            }
            
            // 시장 상태 업데이트 (메시지에서 추출)
            const marketConditionElement = card.querySelector('.market-condition');
            if (marketConditionElement) {
                let marketState = '대기중';
                let marketClass = 'neutral';
                
                // 1. marketCondition에서 확인
                if (data.marketCondition) {
                    marketState = translateCondition(data.marketCondition);
                    if (data.marketCondition === 'OVERBOUGHT') {
                        marketClass = 'overbought';
                    } else if (data.marketCondition === 'OVERSOLD') {
                        marketClass = 'oversold';
                    }
                } 
                // 2. message에서 상태 추출 (marketCondition이 null인 경우)
                else if (data.message) {
                    if (data.message.includes('과매수 상태')) {
                        marketState = '과매수 상태';
                        marketClass = 'overbought';
                    } else if (data.message.includes('과매도 상태')) {
                        marketState = '과매도 상태';
                        marketClass = 'oversold';
                    } else if (data.message.includes('중립 상태')) {
                        marketState = '중립 상태';
                    }
                }
                
                marketConditionElement.textContent = marketState;
                marketConditionElement.className = 'market-condition ' + marketClass;
            }
            
            // 매수 신호 강도 업데이트 (메시지에서 추출)
            const signalStrengthElement = card.querySelector('.signal-value');
            const signalStrengthBar = card.querySelector('.signal-strength-bar');
            
            if (signalStrengthElement && signalStrengthBar) {
                let signalStrength = 0;
                
                // 1. buySignalStrength 필드 확인
                if (data.buySignalStrength !== undefined && data.buySignalStrength > 0) {
                    signalStrength = data.buySignalStrength;
                } 
                // 2. message에서 강도 추출 (buySignalStrength가 0인 경우)
                else if (data.message) {
                    const match = data.message.match(/매수 신호 강도: (\d+\.?\d*)%/);
                    if (match && match[1]) {
                        signalStrength = parseFloat(match[1]);
                    }
                }
                
                signalStrengthElement.textContent = signalStrength.toFixed(1) + '%';
                signalStrengthBar.style.width = signalStrength + '%';
                
                // 클래스 업데이트
                signalStrengthBar.className = 'signal-strength-bar';
                if (signalStrength > 70) {
                    signalStrengthBar.classList.add('strong-buy');
                } else if (signalStrength > 50) {
                    signalStrengthBar.classList.add('moderate-buy');
                } else if (signalStrength > 30) {
                    signalStrengthBar.classList.add('weak-buy');
                } else {
                    signalStrengthBar.classList.add('no-signal');
                }
            }
            
            // 분석 결과 업데이트
            const resultElement = card.querySelector('.result-value');
            if (resultElement && data.analysisResult) {
                let resultText;
                let resultClass;
                
                switch(data.analysisResult) {
                    case 'STRONG_BUY':
                    case 'BUY':
                        resultText = 'BUY';
                        resultClass = 'positive';
                        break;
                    case 'STRONG_SELL':
                    case 'SELL':
                        resultText = 'SELL';
                        resultClass = 'negative';
                        break;
                    case 'HOLD':
                    case 'NEUTRAL':
                    default:
                        resultText = 'HOLD';
                        resultClass = 'neutral';
                }
                
                resultElement.textContent = resultText;
                resultElement.className = 'result-value ' + resultClass;
            }
            
            // 분석 메시지 업데이트
            const messageElement = card.querySelector('.analysis-message');
            if (messageElement && data.message) {
                messageElement.textContent = data.message;
            }
            
            // 시작 버튼 숨기고 중지 버튼 표시
            const startBtn = card.querySelector('.start-button');
            const stopBtn = card.querySelector('.stop-button');
            if (startBtn && stopBtn) {
                startBtn.style.display = 'none';
                stopBtn.style.display = 'inline-block';
            }
            
            console.log('카드 업데이트 완료:', card.id);
        } catch (e) {
            console.error('카드 업데이트 중 오류:', e);
        }
    }
    
    // 시장 상태 번역 함수
    function translateCondition(condition) {
        if (!condition) return '정보 없음';
        
        switch(condition) {
            case 'OVERBOUGHT':
                return '과매수 상태';
            case 'OVERSOLD':
                return '과매도 상태';
            case 'NEUTRAL':
                return '중립 상태';
            default:
                return condition;
        }
    }
    
    // 거래 신호 번역 함수
    function translateSignal(signal) {
        if (!signal) return '정보 없음';
        
        switch(signal) {
            case 'BULLISH':
                return '상승 추세';
            case 'BEARISH':
                return '하락 추세';
            case 'NEUTRAL':
                return '중립 추세';
            case 'OVERBOUGHT':
                return '과매수';
            case 'OVERSOLD':
                return '과매도';
            case 'UPPER_TOUCH':
                return '상단 접촉';
            case 'LOWER_TOUCH':
                return '하단 접촉';
            case 'MIDDLE_CROSS':
                return '중앙선 교차';
            case 'INSIDE':
                return '밴드 내부';
            case 'UPPER_HALF':
                return '상단 구역';
            case 'LOWER_HALF':
                return '하단 구역';
            default:
                return signal;
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
        deleteCard,
        translateCondition,
        translateSignal
    };
})(); 