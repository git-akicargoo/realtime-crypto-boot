// 전역 상태 및 캐시
const state = {
    exchanges: [],
    selectedExchange: '',
    currencies: {},
    symbols: {},
    activeCards: {}
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    console.log('분석 페이지(analysis.html) 초기화');
    
    // 거래소 목록 로드
    ApiService.loadExchanges()
        .then(exchanges => {
            state.exchanges = exchanges;
            populateExchangeSelect(exchanges);
        });
    
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
    
    // 분석 시작 버튼
    const startButton = document.getElementById('startAnalysis');
    if (startButton) {
        startButton.addEventListener('click', startNewAnalysis);
    }
}

function populateExchangeSelect(exchanges) {
    const select = document.getElementById('exchange');
    if (!select) return;
    
    // 기존 옵션 초기화 (첫 번째 옵션 제외)
    while (select.options.length > 1) {
        select.remove(1);
    }
    
    // 거래소 옵션 추가
    exchanges.forEach(exchange => {
        const option = document.createElement('option');
        option.value = exchange;
        option.textContent = exchange.toUpperCase();
        select.appendChild(option);
    });
}

function loadCurrencies(exchange) {
    if (!exchange) return;
    
    ApiService.loadCurrencies(exchange)
        .then(currencies => {
            state.currencies[exchange] = currencies;
            populateCurrencySelect(currencies);
        });
}

function populateCurrencySelect(currencies) {
    const select = document.getElementById('quoteCurrency');
    if (!select) return;
    
    // 기존 옵션 초기화 (첫 번째 옵션 제외)
    while (select.options.length > 1) {
        select.remove(1);
    }
    
    // 화폐 옵션 추가
    currencies.forEach(currency => {
        const option = document.createElement('option');
        option.value = currency;
        option.textContent = currency.toUpperCase();
        select.appendChild(option);
    });
}

function loadSymbols(exchange, currency) {
    if (!exchange || !currency) return;
    
    ApiService.loadSymbols(exchange, currency)
        .then(symbols => {
            state.symbols[`${exchange}-${currency}`] = symbols;
            populateSymbolSelect(symbols);
        });
}

function populateSymbolSelect(symbols) {
    const select = document.getElementById('symbol');
    if (!select) return;
    
    // 기존 옵션 초기화 (첫 번째 옵션 제외)
    while (select.options.length > 1) {
        select.remove(1);
    }
    
    // 코인 옵션 추가
    symbols.forEach(symbol => {
        const option = document.createElement('option');
        option.value = symbol;
        option.textContent = symbol.toUpperCase();
        select.appendChild(option);
    });
}

function startNewAnalysis() {
    const exchange = document.getElementById('exchange').value;
    const quoteCurrency = document.getElementById('quoteCurrency').value;
    const symbol = document.getElementById('symbol').value;
    
    if (!exchange || !quoteCurrency || !symbol) {
        alert('거래소, 화폐, 코인을 모두 선택해주세요.');
        return;
    }
    
    // currencyPair 생성 (거래소별 형식이 다를 수 있음)
    const currencyPair = `${quoteCurrency}-${symbol}`;
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
        WebSocketService.startAnalysis(exchange, currencyPair, symbol, quoteCurrency, card);
        
        // 활성 카드 목록에 추가
        state.activeCards[cardId] = {
            element: card,
            params: { exchange, currencyPair, symbol, quoteCurrency }
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