// 모의거래 서비스 모듈
const SimulTradingService = (function() {
    // 상태 변수
    let state = {
        selectedCard: null,
        initialBalance: 1000000, // 고정값: 100만원
        currentBalance: 1000000,
        signalThreshold: 50, // 기본값: 50%
        takeProfitPercent: 0.1,
        stopLossPercent: 0.1,
        isTrading: false,
        trades: [],
        totalTrades: 0,
        profitPercent: 0,
        cardList: [] // 백엔드에서 가져온 카드 목록
    };
    
    // 웹소켓 연결
    let tradingSocket = null;
    
    // 초기화 함수
    function init() {
        console.log('모의거래 서비스 초기화');
        setupEventListeners();
        // 페이지 로드 시 초기 카드 목록 가져오기
        fetchCardList();
    }
    
    // 이벤트 리스너 설정
    function setupEventListeners() {
        // 카드 선택 이벤트
        const cardSelector = document.getElementById('cardSelector');
        if (cardSelector) {
            // 셀렉트 박스 클릭 시 카드 목록 가져오기
            cardSelector.addEventListener('click', function(event) {
                // 이미 옵션이 로드되어 있고, 첫 번째 옵션 외에 다른 옵션이 있으면 다시 로드하지 않음
                if (this.options.length <= 1) {
                    fetchCardList();
                }
            });
            
            cardSelector.addEventListener('change', handleCardSelection);
        }
        
        // 신호 기준값 버튼 이벤트
        const signalButtons = document.querySelectorAll('.signal-btn');
        signalButtons.forEach(button => {
            button.addEventListener('click', handleSignalButtonClick);
        });
        
        // 모의거래 시작 버튼 이벤트
        const startButton = document.getElementById('startSimulTrading');
        if (startButton) {
            startButton.addEventListener('click', startSimulTrading);
        }
        
        // 익절/손절 기준 입력 이벤트
        const takeProfitInput = document.getElementById('takeProfitPercent');
        if (takeProfitInput) {
            takeProfitInput.addEventListener('change', function() {
                state.takeProfitPercent = parseFloat(this.value);
            });
        }
        
        const stopLossInput = document.getElementById('stopLossPercent');
        if (stopLossInput) {
            stopLossInput.addEventListener('change', function() {
                state.stopLossPercent = parseFloat(this.value);
            });
        }
        
        // 카드 생성/삭제 이벤트 감지하여 카드 선택기 업데이트
        document.addEventListener('cardCreated', updateCardSelector);
        document.addEventListener('cardDeleted', updateCardSelector);
    }
    
    // 카드 선택 핸들러
    function handleCardSelection(event) {
        const cardId = event.target.value;
        
        if (!cardId) {
            clearSelectedCardInfo();
            return;
        }
        
        // 선택된 카드 정보 저장
        state.selectedCard = cardId;
        document.getElementById('selectedCardId').value = cardId;
        
        // 시작 버튼 활성화
        const startButton = document.getElementById('startSimulTrading');
        if (startButton) {
            startButton.disabled = false;
        }
        
        // 선택된 카드의 최신 분석 결과 가져오기
        fetchLatestAnalysisResult(cardId);
    }
    
    // 백엔드에서 최신 분석 결과 가져오기
    function fetchLatestAnalysisResult(cardId) {
        console.log('백엔드에서 카드 ID:', cardId, '의 최신 분석 결과 가져오기');
        
        fetch(`/api/v1/simulation/cards/${cardId}/latest`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('최신 분석 결과를 가져오는데 실패했습니다.');
                }
                return response.json();
            })
            .then(data => {
                console.log('백엔드에서 가져온 최신 분석 결과:', data);
                updateSelectedCardInfo(cardId, data);
            })
            .catch(error => {
                console.error('최신 분석 결과 가져오기 오류:', error);
                // 백엔드 API 호출 실패 시 프론트엔드 상태에서 카드 정보 가져오기
                fallbackToFrontendCardInfo(cardId);
            });
    }
    
    // 백엔드에서 가져온 분석 결과로 선택된 카드 정보 업데이트
    function updateSelectedCardInfo(cardId, analysisData) {
        // 상태 업데이트
        state.selectedCard = {
            cardId: cardId,
            exchange: analysisData.exchange,
            symbol: analysisData.symbol,
            quoteCurrency: analysisData.quoteCurrency,
            currencyPair: analysisData.currencyPair,
            currentPrice: analysisData.currentPrice,
            buySignalStrength: analysisData.buySignalStrength,
            reboundProbability: analysisData.reboundProbability,
            marketCondition: analysisData.marketCondition
        };
        
        // 히든 필드에 카드 정보 저장
        document.getElementById('selectedCardId').value = cardId;
        document.getElementById('selectedCardExchange').value = analysisData.exchange || '';
        document.getElementById('selectedCardCurrencyPair').value = analysisData.currencyPair || '';
        
        console.log('백엔드에서 카드 정보 업데이트됨:', cardId, state.selectedCard);
    }
    
    // 프론트엔드 상태에서 카드 정보 가져오기 (백업 방법)
    function fallbackToFrontendCardInfo(cardId) {
        console.log('프론트엔드 상태에서 카드 정보 가져오기 시도:', cardId);
        
        if (window.state && window.state.activeCards && window.state.activeCards[cardId]) {
            const cardInfo = window.state.activeCards[cardId];
            state.selectedCard = cardInfo;
            
            // 히든 필드에 카드 정보 저장
            document.getElementById('selectedCardId').value = cardId;
            document.getElementById('selectedCardBaseId').value = cardInfo.baseId || '';
            document.getElementById('selectedCardExchange').value = cardInfo.exchange || '';
            document.getElementById('selectedCardCurrencyPair').value = cardInfo.currencyPair || '';
            
            console.log('프론트엔드에서 카드 정보 가져옴:', cardId, cardInfo);
        } else {
            console.error('선택된 카드 정보를 찾을 수 없음:', cardId);
            state.selectedCard = null;
            clearSelectedCardInfo();
        }
    }
    
    // 선택된 카드 정보 초기화
    function clearSelectedCardInfo() {
        document.getElementById('selectedCardId').value = '';
        document.getElementById('selectedCardBaseId').value = '';
        document.getElementById('selectedCardExchange').value = '';
        document.getElementById('selectedCardCurrencyPair').value = '';
    }
    
    // 매수 신호 기준값 버튼 클릭 핸들러
    function handleSignalButtonClick(event) {
        const value = parseInt(event.target.dataset.value);
        state.signalThreshold = value;
        
        // 버튼 활성화 상태 업데이트
        document.querySelectorAll('.signal-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        event.target.classList.add('active');
        
        // 히든 필드에 값 저장
        document.getElementById('selectedSignalThreshold').value = value;
        
        console.log('매수 신호 기준값 설정:', value);
    }
    
    // 카드 목록 가져오기
    function fetchCardList() {
        return fetch('/api/v1/simulation/cards')
            .then(response => {
                if (!response.ok) {
                    throw new Error('카드 목록을 가져오는데 실패했습니다.');
                }
                return response.json();
            })
            .then(data => {
                // 카드 목록 저장
                state.cardList = data;
                
                // 카드 선택기 업데이트
                updateCardSelectorFromBackend();
                
                return data;
            })
            .catch(error => {
                console.error('카드 목록 조회 오류:', error);
                // 오류 발생 시 빈 카드 목록으로 업데이트
                updateCardSelector([]);
                showAlert('분석 카드 목록을 가져오지 못했습니다.');
                
                return [];
            });
    }
    
    // 백엔드에서 가져온 카드 목록으로 선택기 업데이트
    function updateCardSelectorFromBackend() {
        const cardSelector = document.getElementById('cardSelector');
        const noCardsMessage = document.getElementById('noCardsMessage');
        
        if (!cardSelector) return;
        
        // 현재 선택된 값 저장
        const currentValue = cardSelector.value;
        
        // 기존 옵션 제거 (첫 번째 옵션 제외)
        while (cardSelector.options.length > 1) {
            cardSelector.remove(1);
        }
        
        // 카드가 없는 경우 메시지 표시
        if (state.cardList.length === 0) {
            if (noCardsMessage) {
                noCardsMessage.style.display = 'block';
            }
            
            // 시작 버튼 비활성화
            const startButton = document.getElementById('startSimulTrading');
            if (startButton) {
                startButton.disabled = true;
            }
            
            return;
        } else {
            if (noCardsMessage) {
                noCardsMessage.style.display = 'none';
            }
        }
        
        // 카드 목록 추가
        state.cardList.forEach(card => {
            const option = document.createElement('option');
            option.value = card.cardId;
            
            // 카드 정보 포맷팅
            const timestamp = new Date(card.timestamp).toLocaleTimeString();
            option.textContent = `${card.exchange} - ${card.currencyPair} (${timestamp}) [ID: ${card.cardId}]`;
            
            cardSelector.appendChild(option);
        });
        
        // 이전에 선택된 값이 있으면 복원
        if (currentValue && state.cardList.some(card => card.cardId === currentValue)) {
            cardSelector.value = currentValue;
        } else {
            cardSelector.selectedIndex = 0;
            clearSelectedCardInfo();
        }
    }
    
    // 카드 선택기 업데이트 (프론트엔드 상태 기반)
    function updateCardSelector() {
        const cardSelector = document.getElementById('cardSelector');
        if (!cardSelector) return;
        
        // 기존 옵션 제거 (첫 번째 옵션 제외)
        while (cardSelector.options.length > 1) {
            cardSelector.remove(1);
        }
        
        // 활성화된 카드 목록 가져오기
        if (window.state && window.state.activeCards) {
            const activeCards = window.state.activeCards;
            
            // 카드 목록 추가
            for (const cardId in activeCards) {
                const cardInfo = activeCards[cardId];
                const card = cardInfo.card;
                
                if (card) {
                    const exchange = card.getAttribute('data-exchange');
                    const symbol = card.getAttribute('data-symbol');
                    const quoteCurrency = card.getAttribute('data-quote-currency');
                    
                    const option = document.createElement('option');
                    option.value = cardId;
                    option.textContent = `${exchange} - ${symbol}/${quoteCurrency} (${cardId})`;
                    
                    cardSelector.appendChild(option);
                }
            }
        }
    }
    
    // 모의거래 시작
    function startSimulTrading() {
        // 카드 선택 확인
        if (!state.selectedCard) {
            showAlert('분석 카드를 선택해주세요.');
            return;
        }
        
        // 이미 거래 중인지 확인
        if (state.isTrading) {
            showAlert('이미 모의거래가 진행 중입니다.');
            return;
        }
        
        // 거래 상태 초기화
        resetTradingState();
        
        // 거래 시작 상태로 변경
        state.isTrading = true;
        
        // UI 업데이트
        updateTradingUI();
        
        // 웹소켓 연결 시작
        connectTradingWebSocket();
        
        console.log('모의거래 시작:', state);
    }
    
    // 거래 상태 초기화
    function resetTradingState() {
        state.currentBalance = state.initialBalance;
        state.trades = [];
        state.totalTrades = 0;
        state.profitPercent = 0;
        
        // 거래 내역 테이블 초기화
        const tableBody = document.getElementById('tradingHistoryBody');
        if (tableBody) {
            tableBody.innerHTML = '';
        }
        
        // 상태 정보 초기화
        document.getElementById('displayInitialBalance').textContent = FormatUtils.formatNumber(state.initialBalance) + ' KRW';
        document.getElementById('displayCurrentBalance').textContent = FormatUtils.formatNumber(state.currentBalance) + ' KRW';
        document.getElementById('totalTrades').textContent = '0';
        document.getElementById('profitPercent').textContent = '0.00%';
    }
    
    // 거래 UI 업데이트
    function updateTradingUI() {
        const startButton = document.getElementById('startSimulTrading');
        if (startButton) {
            if (state.isTrading) {
                startButton.textContent = '거래 중...';
                startButton.disabled = true;
            } else {
                startButton.textContent = '자동 거래 시작';
                startButton.disabled = false;
            }
        }
    }
    
    // 웹소켓 연결
    function connectTradingWebSocket() {
        // 기존 연결 종료
        if (tradingSocket) {
            tradingSocket.close();
        }
        
        // 웹소켓 URL 생성
        const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${window.location.host}/ws/simul-trading`;
        
        console.log('모의거래 웹소켓 연결 시도:', wsUrl);
        
        // 웹소켓 연결
        tradingSocket = new WebSocket(wsUrl);
        
        // 연결 이벤트 처리
        tradingSocket.onopen = function() {
            console.log('모의거래 웹소켓 연결됨');
            
            // 거래 시작 요청 전송
            const request = {
                action: 'startSimulTrading',
                cardId: document.getElementById('selectedCardId').value,
                baseId: document.getElementById('selectedCardBaseId').value,
                exchange: document.getElementById('selectedCardExchange').value,
                currencyPair: document.getElementById('selectedCardCurrencyPair').value,
                initialBalance: state.initialBalance,
                signalThreshold: state.signalThreshold,
                takeProfitPercent: state.takeProfitPercent,
                stopLossPercent: state.stopLossPercent
            };
            
            console.log('모의거래 요청 전송:', request);
            tradingSocket.send(JSON.stringify(request));
        };
        
        // 메시지 수신 처리
        tradingSocket.onmessage = function(event) {
            try {
                const data = JSON.parse(event.data);
                console.log('모의거래 데이터 수신:', data);
                
                // 에러 체크
                if (data.error) {
                    console.error('모의거래 오류:', data.error);
                    showAlert(`모의거래 오류: ${data.error}`);
                    stopSimulTrading();
                    return;
                }
                
                // 거래 데이터 처리
                processTradingData(data);
                
            } catch (error) {
                console.error('모의거래 메시지 처리 중 오류:', error);
            }
        };
        
        // 오류 처리
        tradingSocket.onerror = function(error) {
            console.error('모의거래 웹소켓 오류:', error);
            showAlert('모의거래 서버 연결 중 오류가 발생했습니다.');
            stopSimulTrading();
        };
        
        // 연결 종료 처리
        tradingSocket.onclose = function() {
            console.log('모의거래 웹소켓 연결 종료');
            state.isTrading = false;
            updateTradingUI();
        };
    }
    
    // 거래 데이터 처리
    function processTradingData(data) {
        // 거래 내역 추가
        if (data.tradeHistory) {
            addTradeToHistory(data.tradeHistory);
        }
        
        // 잔액 업데이트
        if (data.currentBalance) {
            state.currentBalance = data.currentBalance;
            document.getElementById('displayCurrentBalance').textContent = FormatUtils.formatNumber(state.currentBalance) + ' KRW';
        }
        
        // 거래 횟수 업데이트
        if (data.totalTrades !== undefined) {
            state.totalTrades = data.totalTrades;
            document.getElementById('totalTrades').textContent = data.totalTrades;
        }
        
        // 수익률 업데이트
        if (data.profitPercent !== undefined) {
            state.profitPercent = data.profitPercent;
            document.getElementById('profitPercent').textContent = FormatUtils.formatPercent(data.profitPercent);
            
            // 수익률에 따라 색상 변경
            const profitElement = document.getElementById('profitPercent');
            if (profitElement) {
                if (data.profitPercent > 0) {
                    profitElement.className = 'info-value positive';
                } else if (data.profitPercent < 0) {
                    profitElement.className = 'info-value negative';
                } else {
                    profitElement.className = 'info-value';
                }
            }
        }
    }
    
    // 거래 내역 추가
    function addTradeToHistory(trade) {
        const tableBody = document.getElementById('tradingHistoryBody');
        if (!tableBody) return;
        
        // 새 행 생성
        const row = document.createElement('tr');
        
        // 시간
        const timeCell = document.createElement('td');
        timeCell.textContent = FormatUtils.formatDateTime(trade.timestamp);
        row.appendChild(timeCell);
        
        // 상태
        const statusCell = document.createElement('td');
        statusCell.textContent = trade.status;
        statusCell.className = trade.status === 'BUY' ? 'positive' : trade.status === 'SELL' ? 'negative' : '';
        row.appendChild(statusCell);
        
        // 매수가
        const buyPriceCell = document.createElement('td');
        buyPriceCell.textContent = trade.buyPrice ? FormatUtils.formatPrice(trade.buyPrice) : '-';
        row.appendChild(buyPriceCell);
        
        // 매도가
        const sellPriceCell = document.createElement('td');
        sellPriceCell.textContent = trade.sellPrice ? FormatUtils.formatPrice(trade.sellPrice) : '-';
        row.appendChild(sellPriceCell);
        
        // 현재가
        const currentPriceCell = document.createElement('td');
        currentPriceCell.textContent = trade.currentPrice ? FormatUtils.formatPrice(trade.currentPrice) : '-';
        row.appendChild(currentPriceCell);
        
        // 수량
        const quantityCell = document.createElement('td');
        quantityCell.textContent = trade.quantity ? FormatUtils.formatNumber(trade.quantity, 8) : '-';
        row.appendChild(quantityCell);
        
        // 매수금액
        const buyAmountCell = document.createElement('td');
        buyAmountCell.textContent = trade.buyAmount ? FormatUtils.formatNumber(trade.buyAmount) : '-';
        row.appendChild(buyAmountCell);
        
        // 매도금액
        const sellAmountCell = document.createElement('td');
        sellAmountCell.textContent = trade.sellAmount ? FormatUtils.formatNumber(trade.sellAmount) : '-';
        row.appendChild(sellAmountCell);
        
        // 수익률
        const profitPercentCell = document.createElement('td');
        if (trade.profitPercent !== undefined) {
            profitPercentCell.textContent = FormatUtils.formatPercent(trade.profitPercent);
            if (trade.profitPercent > 0) {
                profitPercentCell.className = 'positive';
            } else if (trade.profitPercent < 0) {
                profitPercentCell.className = 'negative';
            }
        } else {
            profitPercentCell.textContent = '-';
        }
        row.appendChild(profitPercentCell);
        
        // 현재가치
        const currentValueCell = document.createElement('td');
        currentValueCell.textContent = trade.currentValue ? FormatUtils.formatNumber(trade.currentValue) : '-';
        row.appendChild(currentValueCell);
        
        // 테이블에 행 추가
        tableBody.appendChild(row);
        
        // 스크롤을 맨 아래로 이동
        const tableContainer = document.querySelector('.table-container');
        if (tableContainer) {
            tableContainer.scrollTop = tableContainer.scrollHeight;
        }
    }
    
    // 모의거래 중지
    function stopSimulTrading() {
        if (tradingSocket) {
            // 중지 요청 전송
            try {
                const request = {
                    action: 'stopSimulTrading',
                    cardId: document.getElementById('selectedCardId').value
                };
                
                tradingSocket.send(JSON.stringify(request));
            } catch (error) {
                console.error('모의거래 중지 요청 전송 중 오류:', error);
            }
            
            // 연결 종료
            tradingSocket.close();
            tradingSocket = null;
        }
        
        state.isTrading = false;
        updateTradingUI();
    }
    
    // 알림 표시
    function showAlert(message) {
        if (window.showCustomAlert) {
            window.showCustomAlert(message);
        } else {
            alert(message);
        }
    }
    
    // 공개 API
    return {
        init,
        updateCardSelector,
        startSimulTrading,
        stopSimulTrading
    };
})();

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    SimulTradingService.init();
}); 