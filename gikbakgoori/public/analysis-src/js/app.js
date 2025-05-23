// 전역 상태 및 캐시
const state = {
    exchanges: [],
    selectedExchange: '',
    currencies: {},
    symbols: {},
    activeCards: {},
    tradingStyle: 'dayTrading', // 기본값 수정
    systemReady: false,
    alertShowing: false // 경고창이 표시 중인지 추적
};

// 페이지 로드 후 즉시 시스템 상태 확인
function checkSystemStatus() {
    if (window.StatusService) {
        console.log('StatusService 존재, 상태 확인 시작');
        return StatusService.validateAndBlockIfNeeded()
            .then(isReady => {
                state.systemReady = isReady;
                console.log('시스템 상태 확인 결과:', isReady);
                return isReady;
            });
    } else {
        console.warn('StatusService가 존재하지 않습니다. 스크립트 로드 순서를 확인하세요.');
        return Promise.resolve(true); // 기본적으로 시스템 사용 가능으로 처리
    }
}

// 주기적 상태 확인 시작 (3초마다)
function startPeriodicStatusCheck() {
    console.log('주기적 상태 확인 설정 (3초 간격)');
    return setInterval(async () => {
        if (!window.StatusService) {
            console.warn('StatusService 사용 불가 - 주기적 확인 건너뜀');
            return;
        }
        
        console.log('주기적 상태 확인 실행');
        const status = await StatusService.checkSystemStatus();
        
        // 시스템 상태가 완전히 정상이면
        if (status.valid && !state.systemReady) {
            StatusService.removeSystemBlockOverlay();
            state.systemReady = true;
            console.log('시스템 상태가 복구되었습니다. 차단이 해제되었습니다.');
        } 
        // 시스템 상태가 이전에 정상이었는데 오류 발생
        else if (!status.valid && state.systemReady) {
            let errorMessage = '현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다:';
            if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
            if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
            if (!status.leaderOk) errorMessage += ' 리더 노드가 아님,';
            
            StatusService.showSystemBlockOverlay(errorMessage.slice(0, -1));
            state.systemReady = false;
        }
        // 시스템 상태가 계속 오류지만 세부 상태가 변경된 경우 (예: Redis만 복구됨)
        else if (!status.valid && !state.systemReady) {
            // 현재 표시 중인 오버레이가 있는지 확인
            const currentOverlay = document.getElementById('systemBlockOverlay');
            if (currentOverlay) {
                // 새 오류 메시지 생성
                let errorMessage = '현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다:';
                if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
                if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
                if (!status.leaderOk) errorMessage += ' 리더 노드가 아님,';
                
                // 오버레이 콘텐츠 업데이트
                StatusService.updateSystemBlockOverlay(errorMessage.slice(0, -1));
            }
        }
    }, 3000); // 3초
}

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', async function() {
    console.log('분석 페이지(analysis.html) 초기화');
    
    // 즉시 "확인 중" 오버레이 표시 (z-index 활용)
    if (window.StatusService) {
        // 임시 "확인 중" 오버레이 표시
        const tempOverlay = document.createElement('div');
        tempOverlay.id = 'tempSystemOverlay';
        Object.assign(tempOverlay.style, {
            position: 'fixed',
            top: '0',
            left: '0',
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            backdropFilter: 'blur(5px)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: '10000',
            color: '#fff',
            textAlign: 'center'
        });
        
        // 세련된 로딩 애니메이션 HTML
        tempOverlay.innerHTML = `
            <div class="loading-container">
                <div class="loading-ring"></div>
                <div class="loading-dot"></div>
                <div class="loading-pulse-ring"></div>
            </div>
            <h2 style="margin-top: 30px; margin-bottom: 10px; font-size: 24px; font-weight: 500;">시스템 연결 확인 중</h2>
            <p style="margin: 0; opacity: 0.8; max-width: 400px; line-height: 1.5;">필요한 서비스와의 연결 상태를 확인하고 있습니다. 잠시만 기다려주세요.</p>
        `;
        
        // 세련된 로딩 애니메이션 CSS
        const style = document.createElement('style');
        style.textContent = `
            .loading-container {
                position: relative;
                width: 120px;
                height: 120px;
            }
            
            .loading-ring {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                border: 4px solid rgba(255, 255, 255, 0.1);
                border-top: 4px solid #fff;
                border-radius: 50%;
                animation: spin 1.5s cubic-bezier(0.68, -0.55, 0.27, 1.55) infinite;
            }
            
            .loading-dot {
                position: absolute;
                top: 10px;
                left: 50%;
                width: 12px;
                height: 12px;
                background-color: #4d90fe;
                border-radius: 50%;
                transform: translateX(-50%);
                box-shadow: 0 0 10px 2px rgba(77, 144, 254, 0.5);
            }
            
            .loading-pulse-ring {
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                width: 90px;
                height: 90px;
                border: 2px solid rgba(77, 144, 254, 0.3);
                border-radius: 50%;
                animation: pulse 2s ease-out infinite;
            }
            
            @keyframes spin {
                to { transform: rotate(360deg); }
            }
            
            @keyframes pulse {
                0% {
                    transform: translate(-50%, -50%) scale(0.8);
                    opacity: 1;
                }
                100% {
                    transform: translate(-50%, -50%) scale(1.2);
                    opacity: 0;
                }
            }
        `;
        
        document.head.appendChild(style);
        document.body.appendChild(tempOverlay);
        document.body.style.overflow = 'hidden'; // 스크롤 방지
        
        // 시스템 상태 확인 (명시적인 타임아웃 없음 - 응답이 올 때까지 대기)
        try {
            console.log('시스템 상태 확인 시작...');
            const status = await StatusService.checkSystemStatus();
            console.log('시스템 상태 확인 완료:', status);
            
            // 임시 오버레이 제거
            tempOverlay.remove();
            document.body.style.overflow = ''; // 스크롤 복원
            
            // 상태에 따른 UI 표시
            if (!status.valid) {
                // 상태가 유효하지 않은 경우
                let errorMessage = '현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다:';
                if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
                if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
                if (!status.leaderOk) errorMessage += ' 리더 노드가 아님,';
                
                StatusService.showSystemBlockOverlay(errorMessage.slice(0, -1));
                state.systemReady = false;
            } else {
                // 정상인 경우
                state.systemReady = true;
                console.log('시스템 상태 정상, UI 초기화 진행...');
                
                // 거래소 목록 로드
                try {
                    const exchanges = await ApiService.loadExchanges();
                    state.exchanges = exchanges;
                    populateExchangeSelect(exchanges);
                } catch (error) {
                    console.error('거래소 목록 로드 실패:', error);
                    StatusService.showStatusAlert('거래소 정보를 불러오는 데 실패했습니다.');
                }
            }
        } catch (error) {
            // 오류 발생시
            console.error('상태 확인 중 오류 발생:', error);
            tempOverlay.remove();
            document.body.style.overflow = ''; // 스크롤 복원
            
            StatusService.showSystemBlockOverlay('시스템 상태 확인 중 오류가 발생했습니다. 네트워크 연결을 확인하세요.');
            state.systemReady = false;
        }
        
        // 주기적 상태 확인 시작
        startPeriodicStatusCheck();
    } else {
        console.warn('StatusService가 존재하지 않습니다');
    }
    
    // 이벤트 리스너 설정 (차단 여부와 관계없이 항상 설정)
    setupEventListeners();
    
    // 테마 설정
    initializeTheme();
});

function setupEventListeners() {
    // 거래소 선택 이벤트
    const exchangeSelect = document.getElementById('exchange');
    if (exchangeSelect) {
        exchangeSelect.addEventListener('change', function() {
            state.selectedExchange = this.value;
            loadCurrencies(this.value);
        });
    }
    
    // 기준 화폐 선택 이벤트
    const currencySelect = document.getElementById('quoteCurrency');
    if (currencySelect) {
        currencySelect.addEventListener('change', function() {
            loadSymbols(state.selectedExchange, this.value);
        });
    }
    
    // 거래 모드 선택 이벤트
    const tradingStyleSelect = document.getElementById('tradingStyle');
    if (tradingStyleSelect) {
        tradingStyleSelect.addEventListener('change', function() {
            state.tradingStyle = this.value;
            console.log('거래 모드 변경:', state.tradingStyle);
        });
    }
    
    // 분석 시작 버튼 이벤트
    const startAnalysisBtn = document.getElementById('startAnalysis');
    if (startAnalysisBtn) {
        startAnalysisBtn.addEventListener('click', async function() {
            console.log('분석 시작 버튼 클릭');
            
            if (!state.systemReady) {
                showAlert('시스템이 준비되지 않았습니다. 인프라 상태를 확인하세요.');
                return;
            }
            
            // 거래소, 코인, 화폐 선택 확인
            const exchange = document.getElementById('exchange').value;
            const symbol = document.getElementById('symbol').value;
            const quoteCurrency = document.getElementById('quoteCurrency').value;
            const tradingStyle = document.getElementById('tradingStyle').value;
            
            if (!exchange || !symbol || !quoteCurrency) {
                showAlert('거래소, 기준 화폐, 코인을 모두 선택하세요.');
                return;
            }
            
            console.log('분석 파라미터:', exchange, symbol, quoteCurrency, tradingStyle);
            
            // 거래쌍 구성
            const currencyPair = `${quoteCurrency}-${symbol}`;
            const displayPair = `${symbol}/${quoteCurrency}`;
            
            // 기본 ID 생성 (거래소-화폐쌍 형식)
            const baseId = `${exchange}-${quoteCurrency}-${symbol}`.toLowerCase();
            
            // 이미 동일한 분석이 진행 중인지 확인 (baseId로 확인)
            const isAlreadyActive = Object.values(state.activeCards).some(cardInfo => 
                cardInfo.baseId === baseId
            );
            
            if (isAlreadyActive) {
                showAlert('이미 동일한 거래쌍의 분석이 진행 중입니다.');
                return;
            }
            
            // 카드 생성
            const card = CardComponent.createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair);
            
            // 카드 정보 저장 (임시 ID로 저장)
            state.activeCards[card.id] = {
                exchange: exchange,
                currencyPair: currencyPair,
                symbol: symbol,
                quoteCurrency: quoteCurrency,
                baseId: baseId,
                card: card
            };
            
            console.log('카드 생성 완료, ID:', card.id);
            
            // 카드 컨테이너에 추가
            document.getElementById('cardsContainer').appendChild(card);
            
            // 중요: 웹소켓 메시지 콜백 등록 - 반드시 startAnalysis 전에 호출
            WebSocketService.onMessage(card.id, function(data) {
                console.log('웹소켓 메시지 콜백 실행:', card.id, data);
                CardComponent.processWebSocketMessage(data);
            });
            
            // 분석 시작
            WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
        });
    }
    
    // 카드 컨테이너 이벤트 위임 (버튼 클릭)
    const cardsContainer = document.getElementById('cardsContainer');
    if (cardsContainer) {
        cardsContainer.addEventListener('click', handleCardButtonClick);
    }
}

// 거래소 선택 목록 채우기
function populateExchangeSelect(exchanges) {
    const select = document.getElementById('exchange');
    if (!select) return;
    
    // 기존 옵션 제거 (첫 번째는 유지)
    while (select.options.length > 1) {
        select.remove(1);
    }
    
    // 새 옵션 추가
    exchanges.forEach(exchange => {
        const option = document.createElement('option');
        option.value = exchange.code;
        option.textContent = exchange.name;
        select.appendChild(option);
    });
}

// 기준 화폐 목록 로드 및 채우기
async function loadCurrencies(exchange) {
    if (!exchange) return;
    
    try {
        const currencies = await ApiService.loadCurrencies(exchange);
        state.currencies[exchange] = currencies;
        
        const select = document.getElementById('quoteCurrency');
        if (!select) return;
        
        // 기존 옵션 제거 (첫 번째는 유지)
        while (select.options.length > 1) {
            select.remove(1);
        }
        
        // 새 옵션 추가
        currencies.forEach(currency => {
            const option = document.createElement('option');
            option.value = currency;
            option.textContent = currency;
            select.appendChild(option);
        });
    } catch (error) {
        console.error(`[${exchange}] 통화 목록 로드 실패:`, error);
    }
}

// 코인 목록 로드 및 채우기
async function loadSymbols(exchange, quoteCurrency) {
    if (!exchange || !quoteCurrency) return;
    
    try {
        // 지원되는 모든 심볼 가져오기
        let symbols = await ApiService.loadSymbols();
        state.symbols = symbols;
        
        const select = document.getElementById('symbol');
        if (!select) return;
        
        // 기존 옵션 제거 (첫 번째는 유지)
        while (select.options.length > 1) {
            select.remove(1);
        }
        
        // 새 옵션 추가
        symbols.forEach(symbol => {
            const option = document.createElement('option');
            option.value = symbol;
            option.textContent = symbol;
            select.appendChild(option);
        });
    } catch (error) {
        console.error(`[${exchange}/${quoteCurrency}] 심볼 목록 로드 실패:`, error);
    }
}

// 카드 버튼 클릭 이벤트 처리
function handleCardButtonClick(event) {
    const target = event.target;
    
    // 버튼이 아니면 무시
    if (!target.matches('button')) return;
    
    // 카드 요소 찾기
    const card = target.closest('.analysis-card');
    if (!card) return;
    
    const cardId = card.id;
    let cardInfo = state.activeCards[cardId];
    
    if (!cardInfo) {
        console.warn('카드 정보를 찾을 수 없음, 정보 생성 시도:', cardId);
        
        // 카드 속성에서 정보 추출
        const exchange = card.getAttribute('data-exchange');
        const currencyPair = card.getAttribute('data-currency-pair');
        const symbol = card.getAttribute('data-symbol');
        const quoteCurrency = card.getAttribute('data-quote-currency');
        const tradingStyle = 'dayTrading'; // 기본값
        
        if (exchange && currencyPair && symbol && quoteCurrency) {
            // 카드 정보 생성
            cardInfo = {
                id: cardId,
                element: card,
                params: {
                    exchange,
                    currencyPair,
                    symbol,
                    quoteCurrency,
                    tradingStyle
                }
            };
            
            // 상태에 추가
            state.activeCards[cardId] = cardInfo;
            console.log('카드 정보 생성 완료:', cardId, cardInfo);
        } else {
            console.error('카드 속성에서 필요한 정보를 추출할 수 없음:', cardId);
            return;
        }
    }
    
    const { exchange, currencyPair, symbol, quoteCurrency, tradingStyle } = cardInfo.params;
    
    // 버튼 액션 처리
    if (target.classList.contains('start-button')) {
        // 시작 버튼 숨기고 중지 버튼 표시
        target.style.display = 'none';
        const stopButton = card.querySelector('.stop-button');
        if (stopButton) stopButton.style.display = 'inline-block';
        
        // 분석 시작 요청
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
    }
    else if (target.classList.contains('stop-button')) {
        // 중지 버튼 숨기고 시작 버튼 표시
        target.style.display = 'none';
        const startButton = card.querySelector('.start-button');
        if (startButton) startButton.style.display = 'inline-block';
        
        // 분석 중지 요청
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
    }
    else if (target.classList.contains('retry-button')) {
        // 재시도 버튼 숨기기
        target.style.display = 'none';
        
        // 분석 다시 시작
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
    }
    else if (target.classList.contains('delete-button')) {
        // 분석 중지 요청
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
        // 카드 삭제
        deleteCard(cardId);
    }
}

// 새 분석 시작
function startNewAnalysis() {
    const exchange = document.getElementById('exchange').value;
    const quoteCurrency = document.getElementById('quoteCurrency').value;
    const symbol = document.getElementById('symbol').value;
    const tradingStyle = document.getElementById('tradingStyle').value || 'dayTrading';
    
    if (!exchange || !quoteCurrency || !symbol) {
        alert('거래소, 기준 화폐, 코인을 모두 선택해주세요.');
        return;
    }
    
    // 통화쌍 생성
    let currencyPair;
    if (exchange === 'UPBIT') {
        currencyPair = `${quoteCurrency}-${symbol}`;
    } else {
        currencyPair = `${symbol}${quoteCurrency}`;
    }
    
    // 표시 통화쌍 설정
    const displayPair = exchange === 'UPBIT' ? currencyPair : `${symbol}/${quoteCurrency}`;
    
    // 카드 생성 및 이벤트 발생
    const card = createCardAndDispatchEvent(exchange, currencyPair, symbol, quoteCurrency, displayPair);
    
    // 추가 정보 저장
    state.activeCards[card.id].tradingStyle = tradingStyle;
    state.activeCards[card.id].active = false;
    
    console.log('새 분석 카드 추가:', card.id, state.activeCards[card.id]);
    
    // 중요: 웹소켓 메시지 콜백 등록 - 반드시 startAnalysis 전에 호출
    WebSocketService.onMessage(card.id, function(data) {
        CardComponent.updateCard(card, data);
    });
    
    // 분석 시작
    WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
}

function initializeTheme() {
    const themeToggle = document.getElementById('themeToggle');
    if (themeToggle) {
        // 저장된 테마 설정 불러오기
        const savedTheme = localStorage.getItem('theme');
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        
        // 저장된 설정이 있거나 시스템이 다크 모드면 다크 모드 활성화
        const isDarkTheme = savedTheme === 'dark' || (!savedTheme && prefersDark);
        
        document.documentElement.setAttribute('data-theme', isDarkTheme ? 'dark' : 'light');
        themeToggle.checked = isDarkTheme;
        
        // 테마 전환 이벤트
        themeToggle.addEventListener('change', function() {
            const theme = this.checked ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', theme);
            localStorage.setItem('theme', theme);
        });
    }
}

// 커스텀 경고창 표시
function showCustomAlert(message) {
    // 이미 표시 중인 경고창이 있으면 제거
    const existingAlert = document.getElementById('customAlert');
    if (existingAlert) {
        existingAlert.remove();
    }
    
    // 새 경고창 생성
    const alertBox = document.createElement('div');
    alertBox.id = 'customAlert';
    alertBox.style.position = 'fixed';
    alertBox.style.top = '50%';
    alertBox.style.left = '50%';
    alertBox.style.transform = 'translate(-50%, -50%)';
    alertBox.style.backgroundColor = 'var(--bg-card)';
    alertBox.style.border = '1px solid var(--border-color)';
    alertBox.style.borderRadius = '8px';
    alertBox.style.padding = '20px';
    alertBox.style.boxShadow = 'var(--shadow)';
    alertBox.style.zIndex = '10000';
    alertBox.style.maxWidth = '80%';
    alertBox.style.textAlign = 'center';
    
    alertBox.innerHTML = `
        <p style="margin-bottom: 20px; color: var(--text-primary)">${message}</p>
        <button id="closeAlert" style="padding: 8px 16px; background: var(--primary-color); color: white; border: none; border-radius: 4px; cursor: pointer;">확인</button>
    `;
    
    document.body.appendChild(alertBox);
    
    // 배경 오버레이 추가
    const overlay = document.createElement('div');
    overlay.id = 'alertOverlay';
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100%';
    overlay.style.height = '100%';
    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
    overlay.style.zIndex = '9999';
    
    document.body.appendChild(overlay);
    
    // 확인 버튼 이벤트
    document.getElementById('closeAlert').addEventListener('click', function() {
        alertBox.remove();
        overlay.remove();
        state.alertShowing = false;
    });
}

// 카드 생성 후 이벤트 발생
function createCardAndDispatchEvent(exchange, currencyPair, symbol, quoteCurrency, displayPair) {
    // 카드 생성
    const card = CardComponent.createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair);
    
    // 카드 정보 저장
    state.activeCards[card.id] = {
        exchange: exchange,
        currencyPair: currencyPair,
        symbol: symbol,
        quoteCurrency: quoteCurrency,
        baseId: `${exchange}-${quoteCurrency}-${symbol}`.toLowerCase(),
        card: card
    };
    
    // 카드를 DOM에 추가
    document.getElementById('cardsContainer').appendChild(card);
    
    // 카드 생성 이벤트 발생
    document.dispatchEvent(new CustomEvent('cardCreated', {
        detail: { cardId: card.id }
    }));
    
    return card;
}

// 카드 삭제 함수
function deleteCard(cardId) {
    console.log('카드 삭제 요청:', cardId);
    
    // 카드 정보 가져오기
    const cardInfo = state.activeCards[cardId];
    if (!cardInfo) {
        console.error('삭제할 카드 정보를 찾을 수 없음:', cardId);
        return;
    }
    
    const card = cardInfo.card;
    if (!card) {
        console.error('삭제할 카드 요소를 찾을 수 없음:', cardId);
        delete state.activeCards[cardId];
        return;
    }
    
    // 웹소켓 연결 종료
    WebSocketService.stopAnalysis(cardId);
    
    // 카드 요소 제거
    const cardsContainer = document.getElementById('cardsContainer');
    if (cardsContainer && card.parentElement === cardsContainer) {
        cardsContainer.removeChild(card);
    }
    
    // 상태에서 제거
    delete state.activeCards[cardId];
    
    // 카드 삭제 이벤트 발생
    document.dispatchEvent(new CustomEvent('cardDeleted', {
        detail: { cardId: cardId }
    }));
    
    console.log('카드 삭제 완료:', cardId);
}
