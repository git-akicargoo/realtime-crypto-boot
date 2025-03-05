// 카드 컴포넌트 관리 모듈
const CardComponent = (function() {
    // 변화율 정보를 저장할 객체 (파일 상단에 추가)
    const priceChangeCache = {};

    // 모의 거래 데이터를 저장할 객체
    const mockTradingData = {};
    // 모의 거래 데이터 초기화 또는 가져오기 함수
    function getMockTradingData(card) {
        const cardId = card.getAttribute("data-card-id") || card.id;
        
        // 모의 거래 데이터가 없으면 초기화
        if (!mockTradingData[cardId]) {
            console.log("[디버깅] 새 모의 거래 데이터 생성:", cardId);
            mockTradingData[cardId] = {
                autoTrading: false,
                currentBalance: 1000000,
                currentPrice: 0,
                trades: [],
                buySignalThreshold: 50,
                signalStrength: 0,
                profitThreshold: 0.1,
                lossThreshold: 0.1,
                lastSignalTime: 0
            };
        }
        
        return mockTradingData[cardId];
    }

    // 카드 생성 함수
    function createCard(exchange, currencyPair, symbol, quoteCurrency, displayPair) {
        console.log('카드 생성:', exchange, currencyPair, symbol, quoteCurrency, displayPair);
        
        const card = document.createElement('div');
        card.className = 'analysis-card';
        card.id = `${exchange}-${currencyPair}`.toLowerCase();
        
        // 데이터 속성 추가
        card.setAttribute('data-exchange', exchange);
        card.setAttribute('data-currency-pair', currencyPair);
        card.setAttribute('data-symbol', symbol);
        card.setAttribute('data-quote-currency', quoteCurrency);
        card.setAttribute('data-card-id', card.id);
        
        console.log('카드 ID:', card.id);
        
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
                
                <!-- 모의 거래 섹션 추가 -->
                <div class="mock-trading-section">
                    <div class="mock-trading-header">
                        <div class="mock-trading-title">모의 거래</div>
                        <div class="mock-trading-toggle-container">
                            <button class="mock-trading-toggle-btn">모의 거래 시작</button>
                        </div>
                    </div>
                    
                    <div class="mock-trading-content" style="display: none;">
                        <div class="mock-trading-header">
                            <div class="mock-balance-info">
                                <div class="mock-balance-row">
                                    <span class="balance-label">초기 자금:</span>
                                    <span class="mock-initial-balance">1,000,000 KRW</span>
                                </div>
                                <div class="mock-balance-row">
                                    <span class="balance-label">현재 자금:</span>
                                    <span class="mock-current-balance">1,000,000 KRW</span>
                                </div>
                            </div>
                            
                            <div class="mock-trading-buttons">
                                <button class="mock-trading-start-btn">모의 거래 시작</button>
                                <button class="mock-trading-stop-btn" style="display: none;">모의 거래 중지</button>
                            </div>
                        </div>
                        
                        <div class="mock-trading-settings">
                            <div class="setting-row">
                                <div class="setting-item">
                                    <span class="setting-label">매수 신호 강도:</span>
                                    <div class="threshold-buttons">
                                        <button class="threshold-btn" data-value="30">30%</button>
                                        <button class="threshold-btn active" data-value="50">50%</button>
                                        <button class="threshold-btn" data-value="70">70%</button>
                                    </div>
                                </div>
                                
                                <div class="setting-item">
                                    <span class="setting-label">익절 기준:</span>
                                    <div class="profit-threshold">
                                        <input type="number" min="0.1" max="20" step="0.1" value="0.1" class="profit-input"> %
                                    </div>
                                </div>
                                
                                <div class="setting-item">
                                    <span class="setting-label">손절 기준:</span>
                                    <div class="loss-threshold">
                                        <input type="number" min="0.1" max="10" step="0.1" value="0.1" class="loss-input"> %
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="mock-trading-history">
                            <h4>거래 내역</h4>
                            <table class="trading-history-table">
                                <thead>
                                    <tr>
                                        <th>시간</th>
                                        <th>상태</th>
                                        <th>매수가</th>
                                        <th>매도가</th>
                                        <th>현재가</th>
                                        <th>수량</th>
                                        <th>매수금액</th>
                                        <th>매도금액</th>
                                        <th>수익률</th>
                                        <th>현재가치</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td colspan="10" class="no-data">거래 내역이 없습니다</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        console.log('카드 HTML 생성 완료');
        
        // 모의 거래 UI 요소 확인
        const mockTradingToggle = card.querySelector('.mock-trading-toggle');
        const mockTradingContent = card.querySelector('.mock-trading-content');
        const mockTradingStartBtn = card.querySelector('.mock-trading-start-btn');
        const mockTradingStopBtn = card.querySelector('.mock-trading-stop-btn');
        const thresholdButtons = card.querySelectorAll('.threshold-btn');
        
        console.log('모의 거래 UI 요소 확인:', {
            mockTradingToggle: mockTradingToggle ? '존재' : '없음',
            mockTradingContent: mockTradingContent ? '존재' : '없음',
            mockTradingStartBtn: mockTradingStartBtn ? '존재' : '없음',
            mockTradingStopBtn: mockTradingStopBtn ? '존재' : '없음',
            thresholdButtons: thresholdButtons.length > 0 ? '존재' : '없음'
        });
        
        // 이벤트 리스너 설정
        setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency);
        
        // 모의 거래 초기화 - 여기서 직접 호출
        try {
            initMockTrading(card, exchange, currencyPair, symbol, quoteCurrency);
        } catch (error) {
            console.error('모의 거래 초기화 중 오류 발생:', error);
        }
        
        return card;
    }
    
    // 모의 거래 초기화 함수
    function initMockTrading(card, exchange, currencyPair, symbol, quoteCurrency) {
        console.log('[디버깅] 모의 거래 초기화:', exchange, currencyPair);
        
        // 모의 거래 UI 생성
        const mockTradingContent = document.createElement('div');
        mockTradingContent.className = 'mock-trading-content';
        mockTradingContent.innerHTML = `
            <div class="mock-trading-header">
                <h3>모의 거래</h3>
                <div class="mock-balance-info">
                    <div class="mock-balance-row">
                        <span class="balance-label">초기 잔액:</span>
                        <span class="mock-initial-balance">1,000,000 KRW</span>
                    </div>
                    <div class="mock-balance-row">
                        <span class="balance-label">현재 잔액:</span>
                        <span class="mock-current-balance">1,000,000 KRW</span>
                    </div>
                </div>
                <div class="mock-trading-buttons">
                    <div class="threshold-setting">
                        <div class="threshold-label">매수 신호 기준값:</div>
                        <div class="threshold-buttons">
                            <button class="threshold-btn" data-value="30">30%</button>
                            <button class="threshold-btn active" data-value="50">50%</button>
                            <button class="threshold-btn" data-value="70">70%</button>
                        </div>
                    </div>
                    <div class="mock-trading-settings">
                        <div class="setting-row">
                            <div class="setting-item">
                                <span class="setting-label">익절 기준(%):</span>
                                <input type="number" class="profit-threshold" min="0.1" max="20" step="0.1" value="0.1">
                            </div>
                            <div class="setting-item">
                                <span class="setting-label">손절 기준(%):</span>
                                <input type="number" class="loss-threshold" min="0.1" max="10" step="0.1" value="0.1">
                            </div>
                        </div>
                    </div>
                    <button class="mock-trading-start-btn">자동 거래 시작</button>
                    <button class="mock-trading-stop-btn" style="display: none;">자동 거래 중지</button>
                </div>
            </div>
            <div class="mock-trading-status">
                <div class="status-message inactive">
                    자동 거래가 비활성화되었습니다. '자동 거래 시작' 버튼을 클릭하여 활성화하세요.
                </div>
            </div>
            <div class="mock-trading-history">
                <h4>거래 내역</h4>
                <table class="trading-history-table">
                    <thead>
                        <tr>
                            <th>시간</th>
                            <th>상태</th>
                            <th>매수가</th>
                            <th>매도가</th>
                            <th>현재가</th>
                            <th>수량</th>
                            <th>매수금액</th>
                            <th>매도금액</th>
                            <th>수익률</th>
                            <th>현재가치</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td colspan="10" class="no-data">거래 내역이 없습니다</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        `;
        
        // 카드에 모의 거래 UI 추가
        card.appendChild(mockTradingContent);
        
        // 모의 거래 이벤트 설정
        setupMockTradingEvents(card, exchange, currencyPair, symbol, quoteCurrency);
        
        // 모의 거래 UI 업데이트
        updateMockTradingUI(card);
    }
    
    // 모의 거래 이벤트 설정 함수
    function setupMockTradingEvents(card, exchange, currencyPair, symbol, quoteCurrency) {
        const cardId = card.getAttribute('data-card-id') || card.id;
        const mockData = getMockTradingData(card);
        
        console.log('[디버깅] 모의 거래 이벤트 설정:', cardId);
        
        // 자동 거래 시작 버튼 이벤트
        const startButton = card.querySelector('.mock-trading-start-btn');
        const stopButton = card.querySelector('.mock-trading-stop-btn');
        
        if (startButton) {
            startButton.addEventListener('click', function() {
                console.log('[디버깅] 자동 거래 시작 버튼 클릭:', cardId);
                
                // 자동 거래 활성화
                mockData.autoTrading = true;
                
                // 버튼 표시 상태 변경
                startButton.style.display = 'none';
                if (stopButton) stopButton.style.display = 'block';
                
                // 현재 신호 강도 로깅
                console.log('[디버깅] 현재 신호 강도:', mockData.signalStrength, '%, 기준값:', mockData.buySignalThreshold, '%');
                
                // UI 업데이트
                updateMockTradingUI(card);
            });
        }
        
        // 자동 거래 중지 버튼 이벤트
        if (stopButton) {
            stopButton.addEventListener('click', function() {
                console.log('[디버깅] 자동 거래 중지 버튼 클릭:', cardId);
                
                // 자동 거래 비활성화
                mockData.autoTrading = false;
                
                // 버튼 표시 상태 변경
                stopButton.style.display = 'none';
                if (startButton) startButton.style.display = 'block';
                
                // UI 업데이트
                updateMockTradingUI(card);
            });
        }
        
        // 매수 신호 기준값 버튼 이벤트
        const thresholdButtons = card.querySelectorAll('.threshold-btn');
        thresholdButtons.forEach(button => {
            button.addEventListener('click', function() {
                const value = parseInt(this.getAttribute('data-value'));
                const oldValue = mockData.buySignalThreshold;
                
                console.log('[디버깅] 매수 신호 기준값 변경:', oldValue, '->', value, '%');
                
                // 기준값 업데이트
                mockData.buySignalThreshold = value;
                
                // 활성 버튼 표시 업데이트
                thresholdButtons.forEach(btn => {
                    btn.classList.remove('active');
                });
                this.classList.add('active');
                
                // UI 업데이트
                updateMockTradingUI(card);
            });
        });
        
        // 익절 기준 입력 이벤트
        const profitThresholdInput = card.querySelector('.profit-threshold');
        if (profitThresholdInput) {
            profitThresholdInput.addEventListener('change', function() {
                const value = parseFloat(this.value);
                if (isNaN(value) || value < 0.1) {
                    this.value = 0.1;
                    mockData.profitThreshold = 0.1;
                } else if (value > 20) {
                    this.value = 20;
                    mockData.profitThreshold = 20;
                } else {
                    mockData.profitThreshold = value;
                }
                
                console.log('[디버깅] 익절 기준 변경:', mockData.profitThreshold, '%');
            });
        }
        
        // 손절 기준 입력 이벤트
        const lossThresholdInput = card.querySelector('.loss-threshold');
        if (lossThresholdInput) {
            lossThresholdInput.addEventListener('change', function() {
                const value = parseFloat(this.value);
                if (isNaN(value) || value < 0.1) {
                    this.value = 0.1;
                    mockData.lossThreshold = 0.1;
                } else if (value > 10) {
                    this.value = 10;
                    mockData.lossThreshold = 10;
                } else {
                    mockData.lossThreshold = value;
                }
                
                console.log('[디버깅] 손절 기준 변경:', mockData.lossThreshold, '%');
            });
        }
    }
    
    // 모의 거래 실행 함수
    function executeMockTrade(card, type, price, quantity, signalStrength) {
        if (!card || !price || price <= 0) {
            console.error('유효하지 않은 거래 파라미터:', card, type, price, quantity);
            return;
        }

        const cardId = card.id;
        const mockData = getMockTradingData(card);
        
        // 현재 시간 포맷팅
        const now = new Date();
        const timeString = now.toLocaleTimeString();
        
        console.log(`[${cardId}] 모의 거래 실행: ${type}, 가격: ${price}, 수량: ${quantity}`);
        
        try {
            if (type === 'BUY') {
                // 매수 금액 계산
                const buyAmount = price * quantity;
                
                // 잔액 확인
                if (mockData.currentBalance < buyAmount) {
                    console.error(`[${cardId}] 매수 실패: 잔액 부족 (필요: ${buyAmount}, 보유: ${mockData.currentBalance})`);
                    return;
                }
                
                // 거래 기록 추가
                const trade = {
                    id: Date.now(),
                    type: 'BUY',
                    time: timeString,
                    price: price,
                    quantity: quantity,
                    amount: buyAmount,
                    signalStrength: signalStrength,
                    isClosed: false
                };
                
                // 거래 추가 및 잔액 업데이트
                mockData.trades.push(trade);
                mockData.currentBalance -= buyAmount;
                mockData.currentHoldings += quantity;
                
                console.log(`[${cardId}] 매수 완료: ${buyAmount} (잔액: ${mockData.currentBalance})`);
            } 
            else if (type === 'SELL') {
                // 보유량 확인
                if (mockData.currentHoldings < quantity) {
                    console.error(`[${cardId}] 매도 실패: 보유량 부족 (필요: ${quantity}, 보유: ${mockData.currentHoldings})`);
                    return;
                }
                
                // 매도 금액 계산
                const sellAmount = price * quantity;
                
                // 거래 기록 추가
                const trade = {
                    id: Date.now(),
                    type: 'SELL',
                    time: timeString,
                    price: price,
                    quantity: quantity,
                    amount: sellAmount,
                    signalStrength: 0,
                    isClosed: false
                };
                
                // 거래 추가 및 잔액 업데이트
                mockData.trades.push(trade);
                mockData.currentBalance += sellAmount;
                mockData.currentHoldings -= quantity;
                
                // 매수 거래 종료 처리
                const buyTrade = mockData.trades.find(t => t.type === 'BUY' && !t.isClosed);
                if (buyTrade) {
                    buyTrade.isClosed = true;
                    
                    // 수익률 계산
                    const profitPercent = ((price / buyTrade.price) - 1) * 100;
                    trade.profitPercent = profitPercent;
                    
                    console.log(`[${cardId}] 매도 완료: ${sellAmount} (수익률: ${profitPercent.toFixed(2)}%, 잔액: ${mockData.currentBalance})`);
                } else {
                    console.log(`[${cardId}] 매도 완료: ${sellAmount} (잔액: ${mockData.currentBalance})`);
                }
            }
            
            // UI 업데이트
            updateMockTradingUI(card);
            updateTradeHistoryTable(card);
            
        } catch (error) {
            console.error(`[${cardId}] 모의 거래 실행 중 오류 발생:`, error);
        }
    }
    
    // 거래 내역 추가 함수
    function addTradeHistory(card, type, price, quantity, amount) {
        const cardId = card.getAttribute("data-card-id") || card.id;
        const mockData = mockTradingData[cardId];
        
        // 거래 내역 객체 생성
        const trade = {
            time: new Date(),
            type: type,
            price: price,
            quantity: quantity,
            amount: amount
        };
        
        // 거래 내역 배열에 추가 (최근 거래가 앞에 오도록)
        mockData.trades.unshift(trade);
        
        // 최대 5개까지만 유지
        if (mockData.trades.length > 5) {
            mockData.trades.pop();
        }
        
        // 거래 내역 테이블 업데이트
        updateTradeHistoryTable(card);
    }
    
    // 거래 내역 테이블 업데이트 함수
    function updateTradeHistoryTable(card) {
        const cardId = card.getAttribute('data-card-id') || card.id;
        const mockData = getMockTradingData(card);
        
        console.log('[디버깅] 거래 내역 테이블 업데이트:', cardId);
        
        const tradingHistoryTable = card.querySelector('.trading-history-table tbody');
        if (!tradingHistoryTable) {
            console.error('[오류] 거래 내역 테이블을 찾을 수 없습니다:', cardId);
            return;
        }
        
        // 테이블 초기화
        tradingHistoryTable.innerHTML = '';
        
        // 거래 내역이 없는 경우
        if (mockData.trades.length === 0) {
            const row = document.createElement('tr');
            row.innerHTML = '<td colspan="10" class="no-data">거래 내역이 없습니다</td>';
            tradingHistoryTable.appendChild(row);
            return;
        }
        
        console.log('[디버깅] 거래 내역 수:', mockData.trades.length);
        
        // 매수 거래 찾기
        const buyTrades = mockData.trades.filter(trade => trade.type === 'BUY');
        console.log('[디버깅] 매수 거래 수:', buyTrades.length);
        
        // 각 매수 거래에 대해 처리
        buyTrades.forEach(buyTrade => {
            // 해당 매수 거래에 대한 매도 거래 찾기
            const sellTrade = mockData.trades.find(trade => 
                trade.type === 'SELL' && trade.buyTradeId === buyTrade.id);
            
            const row = document.createElement('tr');
            row.className = buyTrade.isClosed ? 'closed-trade' : 'active-trade';
            
            // 거래 상태
            const tradeStatus = buyTrade.isClosed ? 
                '<span class="trade-status closed">완료</span>' : 
                '<span class="trade-status active">진행 중</span>';
            
            // 현재가 및 수익률 계산
            const currentPrice = mockData.currentPrice;
            let profitPercent = 0;
            let currentValue = 0;
            
            if (buyTrade.isClosed && sellTrade) {
                // 매도 완료된 경우
                profitPercent = ((sellTrade.price / buyTrade.price) - 1) * 100;
                currentValue = sellTrade.amount;
            } else if (currentPrice > 0) {
                // 진행 중인 경우 (현재가 기준)
                profitPercent = ((currentPrice / buyTrade.price) - 1) * 100;
                currentValue = buyTrade.quantity * currentPrice;
            }
            
            // 수익률 클래스
            const profitClass = profitPercent > 0 ? 'profit' : profitPercent < 0 ? 'loss' : '';
            
            // 매도가 표시
            const sellPriceDisplay = buyTrade.isClosed && sellTrade ? 
                `${FormatUtils.formatNumber(sellTrade.price)}` : '-';
            
            // 매도금액 표시
            const sellAmountDisplay = buyTrade.isClosed && sellTrade ? 
                `${FormatUtils.formatNumber(sellTrade.amount)}` : '-';
            
            // 현재가 표시
            const currentPriceDisplay = !buyTrade.isClosed && currentPrice > 0 ? 
                `${FormatUtils.formatNumber(currentPrice)}` : '-';
            
            // 현재가치 표시
            const currentValueDisplay = !buyTrade.isClosed && currentPrice > 0 ? 
                `<span class="${profitClass}">${FormatUtils.formatNumber(currentValue)}</span>` : 
                buyTrade.isClosed && sellTrade ? 
                `<span class="${profitClass}">${FormatUtils.formatNumber(sellTrade.amount)}</span>` : '-';
            
            row.innerHTML = `
                <td>${buyTrade.time}</td>
                <td>${tradeStatus}</td>
                <td class="price">${FormatUtils.formatNumber(buyTrade.price)}</td>
                <td class="price">${sellPriceDisplay}</td>
                <td class="price">${currentPriceDisplay}</td>
                <td class="quantity">${buyTrade.quantity.toFixed(6)}</td>
                <td class="amount">${FormatUtils.formatNumber(buyTrade.amount)}</td>
                <td class="amount">${sellAmountDisplay}</td>
                <td class="profit-percent"><span class="${profitClass}">${profitPercent.toFixed(2)}%</span></td>
                <td class="current-value">${currentValueDisplay}</td>
            `;
            
            tradingHistoryTable.appendChild(row);
        });
        
        console.log('[디버깅] 거래 내역 테이블 업데이트 완료:', cardId);
    }
    
    // 모의 거래 UI 업데이트 함수
    function updateMockTradingUI(card) {
        const cardId = card.getAttribute('data-card-id') || card.id;
        const mockData = getMockTradingData(card);
        
        console.log('[디버깅] 모의 거래 UI 업데이트:', cardId);
        
        // UI 요소 참조
        const initialBalanceElement = card.querySelector('.mock-initial-balance');
        const currentBalanceElement = card.querySelector('.mock-current-balance');
        const statusMessageElement = card.querySelector('.status-message');
        const profitThresholdInput = card.querySelector('.profit-threshold');
        const lossThresholdInput = card.querySelector('.loss-threshold');
        
        // 잔액 정보 업데이트
        if (initialBalanceElement) {
            initialBalanceElement.textContent = `1,000,000 KRW`;
        }
        
        if (currentBalanceElement) {
            currentBalanceElement.textContent = `${FormatUtils.formatNumber(mockData.currentBalance)} KRW`;
            
            // 수익/손실에 따라 색상 변경
            if (mockData.currentBalance > 1000000) {
                currentBalanceElement.classList.add('profit');
                currentBalanceElement.classList.remove('loss');
            } else if (mockData.currentBalance < 1000000) {
                currentBalanceElement.classList.add('loss');
                currentBalanceElement.classList.remove('profit');
            } else {
                currentBalanceElement.classList.remove('profit', 'loss');
            }
        }
        
        // 익절/손절 입력값 업데이트
        if (profitThresholdInput) {
            profitThresholdInput.value = mockData.profitThreshold;
        }
        
        if (lossThresholdInput) {
            lossThresholdInput.value = mockData.lossThreshold;
        }
        
        // 상태 메시지 업데이트
        if (statusMessageElement) {
            // 활성화된 매수 거래가 있는지 확인
            const activeBuyTrades = mockData.trades.filter(trade => trade.type === 'BUY' && !trade.isClosed);
            
            if (mockData.autoTrading) {
                if (activeBuyTrades.length > 0) {
                    // 활성화된 매수 거래가 있는 경우
                    const buyTrade = activeBuyTrades[0];
                    const profitPercent = ((mockData.currentPrice / buyTrade.price) - 1) * 100;
                    const profitClass = profitPercent > 0 ? 'profit' : profitPercent < 0 ? 'loss' : '';
                    
                    statusMessageElement.innerHTML = `
                        <span class="status-message active">
                            거래 진행 중: 매수가 ${FormatUtils.formatNumber(buyTrade.price)} KRW, 
                            현재 수익률 <strong class="${profitClass}">${profitPercent.toFixed(2)}%</strong><br>
                            익절 기준: <strong>${mockData.profitThreshold}%</strong>, 
                            손절 기준: <strong>${mockData.lossThreshold}%</strong>
                        </span>
                    `;
                    statusMessageElement.className = 'status-message active';
                } else {
                    // 활성화된 거래가 없는 경우 - 매수 신호 대기 중
                    statusMessageElement.innerHTML = `
                        <span class="status-message waiting">
                            매수 신호 대기 중: 현재 신호 강도 <strong>${mockData.signalStrength.toFixed(1)}%</strong>, 
                            기준값 <strong>${mockData.buySignalThreshold}%</strong><br>
                            익절 기준: <strong>${mockData.profitThreshold}%</strong>, 
                            손절 기준: <strong>${mockData.lossThreshold}%</strong>
                        </span>
                    `;
                    statusMessageElement.className = 'status-message waiting';
                }
            } else {
                // 자동 거래가 비활성화된 경우
                statusMessageElement.innerHTML = `
                    <span class="status-message inactive">
                        자동 거래가 비활성화되었습니다. '자동 거래 시작' 버튼을 클릭하여 활성화하세요.
                    </span>
                `;
                statusMessageElement.className = 'status-message inactive';
            }
        }
        
        // 거래 내역 테이블 업데이트
        updateTradeHistoryTable(card);
    }
    
    // 자동 거래 처리 함수
    function processAutoTrading(card, data) {
        if (!card || !data) {
            console.error('유효하지 않은 카드 또는 데이터:', card, data);
            return;
        }

        const cardId = card.id;
        console.log(`[${cardId}] 자동 거래 처리 시작`);

        // 모의 거래 데이터 가져오기
        const mockData = getMockTradingData(card);
        
        // 현재 가격 업데이트
        if (data.currentPrice !== undefined) {
            mockData.currentPrice = data.currentPrice;
        }

        // 신호 강도 추출
        let signalStrength = card.signalStrength || 0;
        
        // 신호 쿨다운 체크 (5초 이내 중복 신호 방지)
        const now = Date.now();
        const cooldownPassed = (now - mockData.lastSignalTime) > 5000;

        // 활성화된 매수 거래가 있는지 확인
        const hasActiveBuyTrade = mockData.trades.some(trade => trade.type === 'BUY' && !trade.isClosed);
        
        // 매수 신호 처리
        const isBuySignal = data.signal === 'BUY' || data.analysisResult === 'BUY';
        
        if (isBuySignal && cooldownPassed && !hasActiveBuyTrade) {
            console.log(`[${cardId}] 매수 신호 감지: ${signalStrength}%, 기준값: ${mockData.buySignalThreshold}%`);
            
            // 매수 조건 확인
            if (signalStrength >= mockData.buySignalThreshold && mockData.currentBalance >= 100000 && mockData.currentPrice > 0) {
                console.log(`[${cardId}] 매수 조건 충족, 거래 실행`);
                
                // 매수 금액 계산 (잔액의 30%)
                const buyAmount = mockData.currentBalance * 0.3;
                const quantity = buyAmount / mockData.currentPrice;
                
                // 매수 거래 실행
                executeMockTrade(card, 'BUY', mockData.currentPrice, quantity, signalStrength);
                
                // 마지막 신호 시간 업데이트
                mockData.lastSignalTime = now;
            }
        }
        // 매도 신호 처리 (활성화된 매수 거래가 있는 경우)
        else if (hasActiveBuyTrade && cooldownPassed) {
            // 가장 최근의 매수 거래 찾기
            const buyTrade = mockData.trades.find(trade => trade.type === 'BUY' && !trade.isClosed);
            
            if (buyTrade && mockData.currentPrice > 0) {
                // 수익률 계산
                const profitPercent = ((mockData.currentPrice / buyTrade.price) - 1) * 100;
                
                // 익절 조건 확인
                if (profitPercent >= mockData.profitThreshold) {
                    console.log(`[${cardId}] 익절 조건 충족, 매도 실행: ${profitPercent.toFixed(2)}% >= ${mockData.profitThreshold}%`);
                    executeMockTrade(card, 'SELL', mockData.currentPrice, buyTrade.quantity, 0);
                    mockData.lastSignalTime = now;
                }
                // 손절 조건 확인
                else if (profitPercent <= -mockData.lossThreshold) {
                    console.log(`[${cardId}] 손절 조건 충족, 매도 실행: ${profitPercent.toFixed(2)}% <= -${mockData.lossThreshold}%`);
                    executeMockTrade(card, 'SELL', mockData.currentPrice, buyTrade.quantity, 0);
                    mockData.lastSignalTime = now;
                }
                // 매도 신호 확인
                else if ((data.signal === 'SELL' || data.analysisResult === 'SELL') && signalStrength < mockData.buySignalThreshold / 2) {
                    console.log(`[${cardId}] 매도 신호 감지, 매도 실행`);
                    executeMockTrade(card, 'SELL', mockData.currentPrice, buyTrade.quantity, 0);
                    mockData.lastSignalTime = now;
                }
            }
        }

        // UI 업데이트
        updateCardUI(card, data);
        updateMockTradingUI(card);
        updateTradeHistoryTable(card);
        
        console.log(`[${cardId}] 자동 거래 처리 완료`);
    }
    
    // 카드 이벤트 설정 함수
    function setupCardEvents(card, exchange, currencyPair, symbol, quoteCurrency) {
        const cardId = card.getAttribute("data-card-id") || card.id;
        
        console.log('카드 이벤트 설정:', cardId);
        
        // 모든 요소 로깅
        console.log('카드 내 모든 요소:', {
            mockTradingToggleBtn: card.querySelector('.mock-trading-toggle-btn'),
            mockTradingContent: card.querySelector('.mock-trading-content'),
            mockTradingSection: card.querySelector('.mock-trading-section'),
            mockTradingHeader: card.querySelector('.mock-trading-header'),
            mockTradingTitle: card.querySelector('.mock-trading-title'),
            mockTradingToggleContainer: card.querySelector('.mock-trading-toggle-container')
        });
        
        // 카드 닫기 버튼 이벤트
        const closeButton = card.querySelector('.close-button');
        if (closeButton) {
            closeButton.addEventListener('click', function() {
                deleteCard(exchange, currencyPair, symbol, quoteCurrency, card);
            });
        }
        
        // 모의 거래 토글 버튼 이벤트 추가
        const mockTradingToggleBtn = card.querySelector('.mock-trading-toggle-btn');
        const mockTradingContent = card.querySelector('.mock-trading-content');
        
        if (mockTradingToggleBtn && mockTradingContent) {
            console.log('모의 거래 토글 버튼 이벤트 설정');
            mockTradingToggleBtn.addEventListener('click', function() {
                console.log('모의 거래 토글 버튼 클릭');
                
                // 현재 표시 상태 확인
                const isVisible = mockTradingContent.style.display !== 'none';
                
                if (isVisible) {
                    // 숨기기
                    mockTradingContent.style.display = 'none';
                    this.textContent = '모의 거래 시작';
                    console.log('모의 거래 섹션 숨김');
                } else {
                    // 표시하기
                    mockTradingContent.style.display = 'block';
                    this.textContent = '모의 거래 숨기기';
                    console.log('모의 거래 섹션 표시');
                }
            });
            
            // 디버깅을 위한 로그 추가
            console.log('모의 거래 콘텐츠 초기 상태:', mockTradingContent.style.display);
        } else {
            console.error('모의 거래 토글 버튼 또는 콘텐츠 요소를 찾을 수 없음');
        }
        
        // 모의 거래 시작/중지 버튼 이벤트
        const mockTradingStartBtn = card.querySelector('.mock-trading-start-btn');
        const mockTradingStopBtn = card.querySelector('.mock-trading-stop-btn');
        
        if (mockTradingStartBtn && mockTradingStopBtn) {
            console.log('모의 거래 시작/중지 버튼 이벤트 설정');
            
            mockTradingStartBtn.addEventListener('click', function() {
                // 모의 거래 데이터 확인
                if (!mockTradingData[cardId]) {
                    console.error('모의 거래 데이터를 찾을 수 없음:', cardId);
                    return;
                }
                
                mockTradingData[cardId].autoTrading = true;
                mockTradingStartBtn.style.display = 'none';
                mockTradingStopBtn.style.display = 'inline-block';
                console.log('자동 거래 시작:', cardId);
            });
            
            mockTradingStopBtn.addEventListener('click', function() {
                // 모의 거래 데이터 확인
                if (!mockTradingData[cardId]) {
                    console.error('모의 거래 데이터를 찾을 수 없음:', cardId);
                    return;
                }
                
                mockTradingData[cardId].autoTrading = false;
                mockTradingStopBtn.style.display = 'none';
                mockTradingStartBtn.style.display = 'inline-block';
                console.log('자동 거래 중지:', cardId);
            });
        } else {
            console.error('모의 거래 시작/중지 버튼을 찾을 수 없음');
        }
        
        // 신호 강도 버튼 이벤트
        const thresholdButtons = card.querySelectorAll('.threshold-btn');
        if (thresholdButtons.length > 0) {
            console.log('신호 강도 버튼 이벤트 설정');
            
            thresholdButtons.forEach(button => {
                button.addEventListener('click', function() {
                    // 모의 거래 데이터 확인
                    if (!mockTradingData[cardId]) {
                        console.error('모의 거래 데이터를 찾을 수 없음:', cardId);
                        return;
                    }
                    
                    // 모든 버튼에서 active 클래스 제거
                    thresholdButtons.forEach(btn => btn.classList.remove('active'));
                    // 클릭한 버튼에 active 클래스 추가
                    this.classList.add('active');
                    
                    const value = parseFloat(this.getAttribute('data-value'));
                    mockTradingData[cardId].buySignalThreshold = value;
                    console.log('매수 신호 강도 기준 변경:', value);
                });
            });
        } else {
            console.error('신호 강도 버튼을 찾을 수 없음');
        }
        
        // 익절/손절 기준 입력 이벤트
        const profitInput = card.querySelector('.profit-input');
        const lossInput = card.querySelector('.loss-input');
        
        if (profitInput) {
            profitInput.addEventListener('change', function() {
                // 모의 거래 데이터 확인
                if (!mockTradingData[cardId]) {
                    console.error('모의 거래 데이터를 찾을 수 없음:', cardId);
                    return;
                }
                
                const value = parseFloat(this.value);
                if (!isNaN(value) && value >= 0.1) {
                    mockTradingData[cardId].profitThreshold = value;
                    console.log('익절 기준 변경:', value);
                } else {
                    // 유효하지 않은 값이면 기본값으로 복원
                    this.value = mockTradingData[cardId].profitThreshold;
                    alert('0.1 이상의 값을 입력해주세요.');
                }
            });
        }
        
        if (lossInput) {
            lossInput.addEventListener('change', function() {
                // 모의 거래 데이터 확인
                if (!mockTradingData[cardId]) {
                    console.error('모의 거래 데이터를 찾을 수 없음:', cardId);
                    return;
                }
                
                const value = parseFloat(this.value);
                if (!isNaN(value) && value >= 0.1) {
                    mockTradingData[cardId].lossThreshold = value;
                    console.log('손절 기준 변경:', value);
                } else {
                    // 유효하지 않은 값이면 기본값으로 복원
                    this.value = mockTradingData[cardId].lossThreshold;
                    alert('0.1 이상의 값을 입력해주세요.');
                }
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
        
        // 모의 거래 데이터 제거
        if (mockTradingData[cardId]) {
            delete mockTradingData[cardId];
        }
    }
    
    // 카드 업데이트 함수
    function updateCard(card, data) {
        if (!card || !data) return;
        
        const cardId = card.getAttribute('data-card-id') || card.id;
        const mockData = getMockTradingData(card);
        
        // 현재 가격 업데이트
        if (data.price) {
            mockData.currentPrice = parseFloat(data.price);
        } else if (data.lastPrice) {
            mockData.currentPrice = parseFloat(data.lastPrice);
        } else if (data.currentPrice) {
            mockData.currentPrice = parseFloat(data.currentPrice);
        }
        
        // 신호 강도 추출 및 업데이트
        if (data.buySignalStrength !== undefined) {
            mockData.signalStrength = parseFloat(data.buySignalStrength);
        } else if (data.signalStrength !== undefined) {
            mockData.signalStrength = parseFloat(data.signalStrength);
        } else if (data.message) {
            // 메시지에서 매수 신호 강도 추출 시도
            const buySignalMatch = data.message.match(/매수 신호 강도: (\d+\.?\d*)%/);
            if (buySignalMatch && buySignalMatch[1]) {
                mockData.signalStrength = parseFloat(buySignalMatch[1]);
            }
        }
        
        // 가격 셀 업데이트
        const priceCell = card.querySelector('.price-cell');
        if (priceCell && data.price) {
            const currentPrice = priceCell.querySelector('.current-price');
            if (currentPrice) {
                currentPrice.textContent = FormatUtils.formatNumber(data.price);
            }
        }
        
        // 결과 셀 업데이트
        const resultCell = card.querySelector('.result-cell');
        if (resultCell && data.analysisResult) {
            const resultValue = resultCell.querySelector('.result-value');
            if (resultValue) {
                resultValue.textContent = getSignalText(data.analysisResult);
                resultValue.className = 'result-value ' + getSignalClass(data.analysisResult);
            }
        }
        
        // 신호 셀 업데이트
        const signalCell = card.querySelector('.signal-cell');
        if (signalCell && data.signal) {
            const signalValue = signalCell.querySelector('.signal-value');
            if (signalValue) {
                signalValue.textContent = getSignalText(data.signal);
                signalValue.className = 'signal-value ' + getSignalClass(data.signal);
            }
        }
        
        // 지표 셀 업데이트
        const indicatorsCell = card.querySelector('.indicators-cell');
        if (indicatorsCell && data.indicators) {
            const indicatorsList = indicatorsCell.querySelector('.indicators-list');
            if (indicatorsList) {
                // 지표 목록 업데이트
                updateIndicators(indicatorsList, data.indicators);
            }
        }
        
        // 시장 상태 셀 업데이트
        const marketCell = card.querySelector('.market-cell');
        if (marketCell && data.marketCondition) {
            const marketValue = marketCell.querySelector('.market-value');
            if (marketValue) {
                marketValue.textContent = getMarketConditionText(data.marketCondition);
            }
        }
        
        // 메시지 셀 업데이트
        const messageCell = card.querySelector('.message-cell');
        if (messageCell && data.message) {
            const analysisMessage = messageCell.querySelector('.analysis-message');
            if (analysisMessage) {
                analysisMessage.textContent = data.message;
            }
        }
        
        // 볼린저 밴드 신호 업데이트
        const bollingerSignal = card.querySelector('.bollinger-signal');
        if (bollingerSignal && data.bollingerBandsSignal) {
            bollingerSignal.textContent = getBollingerText(data.bollingerBandsSignal);
            bollingerSignal.className = 'bollinger-signal ' + getBollingerClass(data.bollingerBandsSignal);
        }
        
        // 신호 강도 업데이트
        const signalStrengthValue = card.querySelector('.signal-strength-value');
        const signalStrengthBar = card.querySelector('.signal-strength-bar');
        if (signalStrengthValue && signalStrengthBar) {
            let strength = card.signalStrength || 0;
            
            if (strength > 0) {
                signalStrengthValue.textContent = strength + '%';
                signalStrengthBar.style.width = strength + '%';
                console.log(`[${cardId}] 신호 강도 업데이트: ${strength}%`);
            }
        }
        
        // 모의 거래 UI 업데이트
        updateMockTradingUI(card);
    }
    
    // 볼린저 밴드 신호 텍스트 변환
    function getBollingerText(signal) {
        switch(signal) {
            case 'UPPER_BREAKOUT':
                return '상단 돌파';
            case 'UPPER_TOUCH':
                return '상단 접촉';
            case 'MIDDLE_CROSS_UP':
                return '중앙선 상향';
            case 'MIDDLE_CROSS_DOWN':
                return '중앙선 하향';
            case 'LOWER_TOUCH':
                return '하단 접촉';
            case 'LOWER_BREAKOUT':
                return '하단 돌파';
            case 'NEUTRAL':
            default:
                return '중립';
        }
    }
    
    // 볼린저 밴드 신호 클래스 변환
    function getBollingerClass(signal) {
        if (signal.includes('UPPER')) {
            return 'negative';
        } else if (signal.includes('LOWER')) {
            return 'positive';
        } else if (signal === 'MIDDLE_CROSS_UP') {
            return 'positive';
        } else if (signal === 'MIDDLE_CROSS_DOWN') {
            return 'negative';
        } else {
            return 'neutral';
        }
    }
    
    // 시장 상태 텍스트 변환
    function getMarketConditionText(condition) {
        switch(condition) {
            case 'BULLISH':
                return '상승장';
            case 'BEARISH':
                return '하락장';
            case 'OVERBOUGHT':
                return '과매수 상태';
            case 'OVERSOLD':
                return '과매도 상태';
            case 'NEUTRAL':
            default:
                return '중립 상태';
        }
    }
    
    // 신호 텍스트 변환
    function getSignalText(signal) {
        switch(signal) {
            case 'STRONG_BUY':
                return '강력 매수';
            case 'BUY':
                return '매수';
            case 'STRONG_SELL':
                return '강력 매도';
            case 'SELL':
                return '매도';
            case 'HOLD':
            case 'NEUTRAL':
            default:
                return '관망';
        }
    }
    
    // 신호 클래스 변환
    function getSignalClass(signal) {
        if (signal === 'STRONG_BUY' || signal === 'BUY' || signal === 'BULLISH') {
            return 'positive';
        } else if (signal === 'STRONG_SELL' || signal === 'SELL' || signal === 'BEARISH') {
            return 'negative';
        } else if (signal === 'OVERSOLD') {
            return 'positive';
        } else if (signal === 'OVERBOUGHT') {
            return 'negative';
        } else {
            return 'neutral';
        }
    }
    
    // 시장 상태 번역 함수
    function translateCondition(condition) {
        switch(condition) {
            case 'BULLISH':
                return '상승장';
            case 'BEARISH':
                return '하락장';
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
        switch(signal) {
            case 'BULLISH':
                return '상승';
            case 'BEARISH':
                return '하락';
            case 'NEUTRAL':
                return '중립';
            case 'OVERBOUGHT':
                return '과매수';
            case 'OVERSOLD':
                return '과매도';
            default:
                return signal;
        }
    }
    
    // 오류 표시
    function showError(card, errorMessage) {
        console.error('카드 오류:', errorMessage);
        
        // 로딩 표시 숨기기
        const loadingIndicator = card.querySelector('.loading-indicator');
        if (loadingIndicator) {
            loadingIndicator.style.display = 'none';
        }
        
        // 재시도 버튼 표시
        const startBtn = card.querySelector('.start-button');
        const stopBtn = card.querySelector('.stop-button');
        const retryBtn = card.querySelector('.retry-button');
        
        if (startBtn && stopBtn && retryBtn) {
            startBtn.style.display = 'none';
            stopBtn.style.display = 'none';
            retryBtn.style.display = 'inline-block';
        }
    }
    
    // 메시지에서 신호 강도 추출 함수
    function extractSignalStrengthFromMessage(message) {
        if (!message) return 0;
        
        // 메시지에서 매수 신호 강도 추출 시도
        const buySignalMatch = message.match(/매수 신호 강도: (\d+\.?\d*)%/);
        if (buySignalMatch && buySignalMatch[1]) {
            return parseFloat(buySignalMatch[1]);
        }
        
        return 0;
    }
    
    // 웹소켓 메시지 처리 함수
    function processWebSocketMessage(card, data) {
        // 유효성 검사
        if (!card || !data) {
            console.error('유효하지 않은 카드 또는 데이터:', card, data);
            return;
        }

        const cardId = card.id;
        console.log(`[${cardId}] WebSocket 메시지 처리 시작`);
        console.log(`[${cardId}] 수신된 데이터:`, data);

        // 현재 가격 업데이트
        if (data.currentPrice !== undefined) {
            console.log(`[${cardId}] 현재 가격 업데이트: ${data.currentPrice}`);
            // 카드 객체에 현재 가격 저장
            card.currentPrice = data.currentPrice;
        }

        // 신호 강도 직접 사용
        if (data.buySignalStrength !== undefined) {
            console.log(`[${cardId}] 매수 신호 강도 업데이트: ${data.buySignalStrength}%`);
            // 카드 객체에 신호 강도 저장
            card.signalStrength = data.buySignalStrength;
        }

        // 자동 거래가 활성화되어 있지 않은 경우 UI 업데이트
        if (!card.autoTrading) {
            // UI 업데이트 함수 호출
            updateCardUI(card, data);
        } else {
            // 자동 거래 로직 처리
            processAutoTrading(card, data);
        }

        console.log(`[${cardId}] WebSocket 메시지 처리 완료`);
    }
    
    // UI 업데이트 함수
    function updateCardUI(card, data) {
        if (!card || !data) {
            console.error('유효하지 않은 카드 또는 데이터:', card, data);
            return;
        }

        const cardId = card.id;
        console.log(`[${cardId}] 카드 UI 업데이트 시작`);

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
            const changeValue = data.priceChangePercent;
            priceChange.textContent = FormatUtils.formatPercent(changeValue);
            priceChange.className = 'price-change ' + (changeValue > 0 ? 'positive' : changeValue < 0 ? 'negative' : 'neutral');
            console.log(`[${cardId}] 가격 변화 업데이트: ${changeValue}%`);
        }

        // 분석 결과 업데이트
        const resultValue = card.querySelector('.result-value');
        if (resultValue && data.analysisResult) {
            resultValue.textContent = data.analysisResult;
            resultValue.className = 'result-value ' + 
                (data.analysisResult === 'BUY' || data.analysisResult === 'STRONG_BUY' ? 'positive' : 
                 data.analysisResult === 'SELL' || data.analysisResult === 'STRONG_SELL' ? 'negative' : 'neutral');
            console.log(`[${cardId}] 분석 결과 업데이트: ${data.analysisResult}`);
        }

        // 신호 업데이트 (SMA, RSI, 볼린저 밴드 등)
        updateSignals(card, data);

        // 시장 상태 업데이트
        const marketCondition = card.querySelector('.market-condition');
        if (marketCondition && data.marketCondition) {
            const condition = data.marketCondition;
            marketCondition.textContent = condition;
            marketCondition.className = 'market-condition ' + 
                (condition === 'OVERBOUGHT' ? 'negative' : 
                 condition === 'OVERSOLD' ? 'positive' : 'neutral');
            console.log(`[${cardId}] 시장 상태 업데이트: ${condition}`);
        }

        // 메시지 업데이트
        const messageElement = card.querySelector('.analysis-message');
        if (messageElement && data.message) {
            messageElement.textContent = data.message;
            console.log(`[${cardId}] 메시지 업데이트: ${data.message}`);
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

        // 신호 강도 업데이트
        const signalStrengthValue = card.querySelector('.signal-strength-value');
        const signalStrengthBar = card.querySelector('.signal-strength-bar');
        if (signalStrengthValue && signalStrengthBar) {
            let strength = data.buySignalStrength !== undefined ? data.buySignalStrength : (card.signalStrength || 0);
            
            if (strength > 0) {
                signalStrengthValue.textContent = strength.toFixed(1) + '%';
                signalStrengthBar.style.width = strength + '%';
                console.log(`[${cardId}] 신호 강도 업데이트: ${strength}%`);
            }
        }

        console.log(`[${cardId}] 카드 UI 업데이트 완료`);
    }
    
    // 신호 업데이트 함수
    function updateSignals(card, data) {
        if (!card || !data) return;

        const cardId = card.id;
        
        // SMA 신호 업데이트
        const smaSignal = card.querySelector('.sma-signal');
        if (smaSignal && data.smaSignal) {
            const signalValue = data.smaSignal;
            smaSignal.textContent = signalValue;
            smaSignal.className = 'indicator-value sma-signal ' + 
                (signalValue === 'BULLISH' || signalValue === 'MODERATELY_BULLISH' || signalValue === 'SLIGHTLY_BULLISH' ? 'positive' : 
                 signalValue === 'BEARISH' || signalValue === 'MODERATELY_BEARISH' || signalValue === 'SLIGHTLY_BEARISH' ? 'negative' : 'neutral');
            console.log(`[${cardId}] SMA 신호 업데이트: ${signalValue}`);
        }
        
        // RSI 값 업데이트
        const rsiValue = card.querySelector('.rsi-value');
        if (rsiValue && data.rsiValue !== undefined) {
            rsiValue.textContent = data.rsiValue.toFixed(2);
            rsiValue.className = 'indicator-value rsi-value ' + 
                (data.rsiSignal === 'OVERSOLD' ? 'positive' : 
                 data.rsiSignal === 'OVERBOUGHT' ? 'negative' : 'neutral');
            console.log(`[${cardId}] RSI 값 업데이트: ${data.rsiValue}`);
        }
        
        // 거래량 변화 업데이트
        const volumeSignal = card.querySelector('.volume-signal');
        if (volumeSignal && data.volumeChangePercent !== undefined) {
            volumeSignal.textContent = FormatUtils.formatPercent(data.volumeChangePercent);
            
            let volumeClass = 'neutral';
            if (data.volumeChangePercent > 20) {
                volumeClass = 'positive';
            } else if (data.volumeChangePercent < -20) {
                volumeClass = 'negative';
            }
            
            volumeSignal.className = 'indicator-value volume-signal ' + volumeClass;
            console.log(`[${cardId}] 거래량 변화 업데이트: ${data.volumeChangePercent}%`);
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