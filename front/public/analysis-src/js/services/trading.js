// 모의 거래 서비스 모듈
const TradingService = (function() {
    // 모의 거래 기록 저장
    const simulationHistory = {};
    
    // 매수 신호 기준값 (카드별로 다르게 설정 가능)
    const buySignalThresholds = {};
    
    // 모의 거래 활성화 상태 추적
    const simulationEnabled = {};
    
    // 모의 거래 실행
    function executeSimulatedTrade(data, cardId) {
        console.log('모의 거래 실행 시도:', cardId, data);
        
        // 모의 거래가 활성화되지 않은 경우 실행하지 않음
        if (!simulationEnabled[cardId]) {
            console.log('모의 거래가 활성화되지 않았습니다:', cardId);
            return null;
        }
        
        // 메시지에서 매수 신호 강도 추출 시도
        let signalStrength = data.buySignalStrength || 0;
        
        // 메시지에서 매수 신호 강도 추출 (buySignalStrength가 0인 경우)
        if (signalStrength === 0 && data.message) {
            const match = data.message.match(/매수 신호 강도: (\d+\.?\d*)%/);
            if (match && match[1]) {
                signalStrength = parseFloat(match[1]);
                console.log(`메시지에서 추출한 매수 신호 강도: ${signalStrength}%`);
            }
        }
        
        // 기준값 체크 - 미설정시 기본값 50% 사용
        const threshold = buySignalThresholds[cardId] || 50;
        
        // 매수 신호 강도가 기준값보다 낮으면 거래 실행 안함
        if (signalStrength < threshold) {
            console.log(`매수 신호 강도(${signalStrength}%)가 기준값(${threshold}%)보다 낮아 거래 실행 안함`);
            return null;
        }
        
        const timestamp = new Date().toISOString();
        
        // 거래 정보 생성 (실제 데이터의 signalStrength 사용)
        const tradeInfo = {
            timestamp: timestamp,
            exchange: data.exchange,
            symbol: data.symbol,
            quoteCurrency: data.quoteCurrency,
            price: data.currentPrice,
            action: 'BUY', 
            signalStrength: signalStrength,
            amount: 1 // 기본 수량
        };
        
        // 카드별 거래 기록 저장
        if (!simulationHistory[cardId]) {
            simulationHistory[cardId] = [];
        }
        simulationHistory[cardId].unshift(tradeInfo); // 최신 거래가 맨 앞에 오도록
        
        console.log('모의 거래 실행 완료:', tradeInfo);
        return tradeInfo;
    }
    
    // 모의 거래 활성화/비활성화
    function toggleSimulation(cardId, enabled, threshold = 50) {
        simulationEnabled[cardId] = enabled;
        if (enabled) {
            buySignalThresholds[cardId] = threshold;
        }
        return enabled;
    }
    
    // 모의 거래 상태 확인
    function isSimulationEnabled(cardId) {
        return simulationEnabled[cardId] || false;
    }
    
    // 매수 신호 기준값 가져오기
    function getBuySignalThreshold(cardId) {
        return buySignalThresholds[cardId] || 50;
    }
    
    // 거래 기록 가져오기
    function getTradeHistory(cardId) {
        return simulationHistory[cardId] || [];
    }
    
    // 공개 API
    return {
        executeSimulatedTrade,
        toggleSimulation,
        isSimulationEnabled,
        getBuySignalThreshold,
        getTradeHistory
    };
})();

// 전역 객체로 등록
window.TradingService = TradingService; 