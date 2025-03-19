// API 서비스 모듈
const ApiService = (function() {
    // 기본 API URL
    const API_BASE_URL = '/api/v1';
    
    // 거래소 및 지원 통화 정보 로드
    async function loadSupportedPairs() {
        try {
            const response = await fetch(`${API_BASE_URL}/config/supported-pairs`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('지원 통화쌍 로드 중 오류:', error);
            return { exchanges: {}, symbols: [] };
        }
    }
    
    // 거래소 목록 로드
    async function loadExchanges() {
        try {
            const data = await loadSupportedPairs();
            // 거래소 객체를 배열로 변환
            return Object.keys(data.exchanges || {}).map(code => ({
                code: code,
                name: code.charAt(0) + code.slice(1).toLowerCase()
            }));
        } catch (error) {
            console.error('거래소 목록 로드 중 오류:', error);
            return [];
        }
    }
    
    // 거래소별 지원 통화 로드
    async function loadCurrencies(exchange) {
        try {
            const data = await loadSupportedPairs();
            const exchangeData = data.exchanges || {};
            return exchangeData[exchange.toUpperCase()] || [];
        } catch (error) {
            console.error(`${exchange} 지원 통화 로드 중 오류:`, error);
            return [];
        }
    }
    
    // 지원 심볼 로드
    async function loadSymbols() {
        try {
            const data = await loadSupportedPairs();
            return data.symbols || [];
        } catch (error) {
            console.error('지원 심볼 로드 중 오류:', error);
            return [];
        }
    }
    
    // 시스템 상태 확인 - 백엔드에 해당 API가 없으므로 더미 응답 반환
    async function checkSystemStatus() {
        // 실제 API가 없으므로 더미 데이터 반환
        return {
            redis: true,
            kafka: true,
            service: true
        };
    }
    
    // 공개 API
    return {
        loadSupportedPairs,
        loadExchanges,
        loadCurrencies,
        loadSymbols,
        checkSystemStatus
    };
})(); 