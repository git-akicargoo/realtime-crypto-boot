// API 서비스 모듈
const ApiService = (function() {
    // 기본 API URL
    const API_BASE_URL = 'http://localhost:8080/api/v1';
    
    // 거래소 목록 로드
    async function loadExchanges() {
        try {
            const response = await fetch(`${API_BASE_URL}/exchange/list`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data.exchanges || [];
        } catch (error) {
            console.error('거래소 목록 로드 중 오류:', error);
            return [];
        }
    }
    
    // 화폐 목록 로드
    async function loadCurrencies(exchange) {
        try {
            const response = await fetch(`${API_BASE_URL}/exchange/${exchange}/currencies`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data.currencies || [];
        } catch (error) {
            console.error('화폐 목록 로드 중 오류:', error);
            return [];
        }
    }
    
    // 코인 목록 로드
    async function loadSymbols(exchange, currency) {
        try {
            const response = await fetch(`${API_BASE_URL}/exchange/${exchange}/symbols?currency=${currency}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data.symbols || [];
        } catch (error) {
            console.error('코인 목록 로드 중 오류:', error);
            return [];
        }
    }
    
    // 과거 분석 데이터 로드 (추후 구현)
    async function loadHistoricalData(exchange, symbol, quoteCurrency, limit = 10) {
        try {
            const response = await fetch(
                `${API_BASE_URL}/analysis/history?exchange=${exchange}&symbol=${symbol}&quoteCurrency=${quoteCurrency}&limit=${limit}`
            );
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data.history || [];
        } catch (error) {
            console.error('과거 데이터 로드 중 오류:', error);
            return [];
        }
    }
    
    // 분석 설정 저장 (추후 구현)
    async function saveAnalysisConfig(config) {
        try {
            const response = await fetch(`${API_BASE_URL}/analysis/config`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(config)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('분석 설정 저장 중 오류:', error);
            return { success: false, error: error.message };
        }
    }
    
    // 분석 설정 로드 (추후 구현)
    async function loadAnalysisConfig() {
        try {
            const response = await fetch(`${API_BASE_URL}/analysis/config`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('분석 설정 로드 중 오류:', error);
            return { success: false, error: error.message };
        }
    }
    
    // 공개 API
    return {
        loadExchanges,
        loadCurrencies,
        loadSymbols,
        loadHistoricalData,
        saveAnalysisConfig,
        loadAnalysisConfig
    };
})(); 