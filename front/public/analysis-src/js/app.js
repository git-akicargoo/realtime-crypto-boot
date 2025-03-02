// 전역 상태 및 캐시
const state = {
    exchanges: [],
    selectedExchange: '',
    currencies: {},
    symbols: {},
    activeCards: {},
    tradingStyle: 'dayTrading', // 기본값 수정
    systemReady: false
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', async function() {
    console.log('분석 페이지(analysis.html) 초기화');
    
    // 시스템 상태 확인
    if (window.StatusService) {
        state.systemReady = await StatusService.validateSystemStatus();
        if (!state.systemReady) {
            console.warn('시스템 상태 이상: 일부 기능이 제한될 수 있습니다');
        }
    }
    
    // 거래소 목록 로드
    try {
        const exchanges = await ApiService.loadExchanges();
        state.exchanges = exchanges;
        populateExchangeSelect(exchanges);
    } catch (error) {
        console.error('거래소 목록 로드 실패:', error);
        StatusService.showStatusAlert('거래소 정보를 불러오는 데 실패했습니다.');
    }
    
    // 이벤트 리스너 설정
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
        startAnalysisBtn.addEventListener('click', function() {
            if (!state.systemReady) {
                const confirmStart = confirm('시스템 상태에 문제가 있습니다. 그래도 분석을 시작하시겠습니까?');
                if (!confirmStart) return;
            }
            startNewAnalysis();
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
    const cardInfo = state.activeCards[cardId];
    
    if (!cardInfo) {
        console.error('카드 정보를 찾을 수 없음:', cardId);
        return;
    }
    
    const { exchange, currencyPair, symbol, quoteCurrency, tradingStyle } = cardInfo.params;
    
    // 버튼 액션 처리
    if (target.classList.contains('start-button')) {
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
    } else if (target.classList.contains('stop-button')) {
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
    } else if (target.classList.contains('retry-button')) {
        // 재시도 버튼 숨기기
        target.style.display = 'none';
        // 분석 다시 시작
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, tradingStyle);
    } else if (target.classList.contains('delete-button')) {
        // 연결 종료
        WebSocketService.stopAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
        // 카드 삭제
        CardComponent.deleteCard(card);
        // 활성 카드 목록에서 제거
        delete state.activeCards[cardId];
    }
}

// 새 분석 시작
function startNewAnalysis() {
    const exchange = document.getElementById('exchange').value;
    const quoteCurrency = document.getElementById('quoteCurrency').value;
    const symbol = document.getElementById('symbol').value;
    
    // 입력 검증
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
    
    const displayPair = `${symbol}/${quoteCurrency}`;
    const cardId = `${exchange}-${currencyPair}`.toLowerCase();
    
    // 이미 같은 분석이 있는지 확인
    if (state.activeCards[cardId]) {
        alert('이미 같은 분석이 추가되어 있습니다.');
        return;
    }
    
    // 카드 생성 및 분석 시작
    const card = CardComponent.createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair);
    const container = document.getElementById('cardsContainer');
    if (container) {
        container.appendChild(card);
        
        // 분석 시작
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card, state.tradingStyle);
        
        // 활성 카드 목록에 추가
        state.activeCards[cardId] = {
            element: card,
            params: { 
                exchange, 
                currencyPair, 
                symbol, 
                quoteCurrency, 
                tradingStyle: state.tradingStyle 
            }
        };
    }
    
    // 선택 초기화
    document.getElementById('symbol').selectedIndex = 0;
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