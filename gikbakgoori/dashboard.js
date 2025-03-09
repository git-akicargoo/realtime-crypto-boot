// // 대시보드 컴포넌트 - 분석 데이터 리스트 및 요약 표시
// const DashboardComponent = (function() {
//     // 최근 데이터 포인트 저장 (최대 100개)
//     const dataPoints = [];
//     const MAX_DATA_POINTS = 100;
    
//     // 데이터 포인트 추가
//     function addDataPoint(data) {
//         if (!data) return;
        
//         // 동일한 거래소-통화 쌍에 대한 기존 데이터 찾기
//         const key = `${data.exchange}-${data.currencyPair}`;
//         const existingIndex = dataPoints.findIndex(dp => 
//             `${dp.exchange}-${dp.currencyPair}` === key
//         );
        
//         // 기존 데이터가 있으면 업데이트, 없으면 추가
//         if (existingIndex >= 0) {
//             dataPoints[existingIndex] = data;
//         } else {
//             // 최대 개수 초과 시 가장 오래된 항목 제거
//             if (dataPoints.length >= MAX_DATA_POINTS) {
//                 dataPoints.shift();
//             }
//             dataPoints.push(data);
//         }
        
//         // 대시보드 UI 업데이트
//         updateDashboard();
//     }
    
//     // 대시보드 UI 업데이트
//     function updateDashboard() {
//         const container = document.getElementById('dataDashboard');
//         if (!container) return;
        
//         // 이전 내용 지우고 새로운 데이터로 업데이트
//         container.innerHTML = '';
        
//         if (dataPoints.length === 0) {
//             container.innerHTML = `
//                 <div class="placeholder-message">
//                     분석 데이터가 없습니다. 분석을 시작하면 여기에 표시됩니다.
//                 </div>
//             `;
//             return;
//         }
        
//         // 테이블 생성
//         const table = document.createElement('table');
//         table.className = 'data-table';
        
//         // 테이블 헤더
//         table.innerHTML = `
//             <thead>
//                 <tr>
//                     <th>거래소</th>
//                     <th>통화쌍</th>
//                     <th>현재가</th>
//                     <th>변동</th>
//                     <th>RSI</th>
//                     <th>신호강도</th>
//                     <th>결과</th>
//                 </tr>
//             </thead>
//             <tbody></tbody>
//         `;
        
//         const tbody = table.querySelector('tbody');
        
//         // 데이터 행 추가
//         dataPoints.forEach(data => {
//             const row = document.createElement('tr');
//             const priceChangeClass = data.priceChangePercent > 0 ? 'positive' : 
//                                     data.priceChangePercent < 0 ? 'negative' : 'neutral';
            
//             const resultClass = data.analysisResult === 'BUY' ? 'positive' : 
//                               data.analysisResult === 'SELL' ? 'negative' : 'neutral';
            
//             row.innerHTML = `
//                 <td>${data.exchange}</td>
//                 <td>${data.symbol}/${data.quoteCurrency}</td>
//                 <td>${FormatUtils.formatPrice(data.currentPrice)}</td>
//                 <td class="${priceChangeClass}">${FormatUtils.formatPercent(data.priceChangePercent)}</td>
//                 <td>${data.rsiValue ? data.rsiValue.toFixed(1) : '-'}</td>
//                 <td>
//                     <div class="mini-signal-bar">
//                         <div class="mini-signal-bar-fill ${getSignalClass(data.buySignalStrength)}" 
//                              style="width: ${data.buySignalStrength}%"></div>
//                     </div>
//                     ${data.buySignalStrength ? data.buySignalStrength.toFixed(1) : 0}%
//                 </td>
//                 <td class="${resultClass}">${getResultText(data.analysisResult)}</td>
//             `;
            
//             // 행 클릭 이벤트 - 상세 정보 표시
//             row.addEventListener('click', function() {
//                 showDetailModal(data);
//             });
            
//             tbody.appendChild(row);
//         });
        
//         container.appendChild(table);
//     }
    
//     // 시그널 강도에 따른 클래스 반환
//     function getSignalClass(strength) {
//         if (!strength) return 'weak-buy';
//         if (strength >= 70) return 'strong-buy';
//         if (strength >= 40) return 'moderate-buy';
//         return 'weak-buy';
//     }
    
//     // 결과 텍스트 변환
//     function getResultText(result) {
//         switch(result) {
//             case 'BUY': return '매수';
//             case 'SELL': return '매도';
//             case 'NEUTRAL': return '중립';
//             default: return result || '중립';
//         }
//     }
    
//     // 상세 정보 모달 표시
//     function showDetailModal(data) {
//         // 이미 모달이 있으면 제거
//         const existingModal = document.querySelector('.detail-modal');
//         if (existingModal) {
//             existingModal.remove();
//         }
        
//         // 모달 생성
//         const modal = document.createElement('div');
//         modal.className = 'detail-modal';
        
//         modal.innerHTML = `
//             <div class="modal-content">
//                 <div class="modal-header">
//                     <h3>${data.exchange} - ${data.symbol}/${data.quoteCurrency} 상세 정보</h3>
//                     <button class="close-modal">×</button>
//                 </div>
//                 <div class="modal-body">
//                     <div class="detail-grid">
//                         <div class="detail-item">
//                             <div class="detail-label">분석 시간:</div>
//                             <div class="detail-value">${FormatUtils.formatDateTime(data.analysisTime)}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">현재가:</div>
//                             <div class="detail-value">${FormatUtils.formatPrice(data.currentPrice)}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">가격 변동:</div>
//                             <div class="detail-value ${data.priceChangePercent > 0 ? 'positive' : data.priceChangePercent < 0 ? 'negative' : 'neutral'}">${FormatUtils.formatPercent(data.priceChangePercent)}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">RSI:</div>
//                             <div class="detail-value">${data.rsiValue ? data.rsiValue.toFixed(1) : '-'}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">볼린저 밴드:</div>
//                             <div class="detail-value">${data.bollingerSignal || '-'}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">SMA 신호:</div>
//                             <div class="detail-value">${data.smaSignal || '-'}</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">매수 신호 강도:</div>
//                             <div class="detail-value">${data.buySignalStrength ? data.buySignalStrength.toFixed(1) : 0}%</div>
//                         </div>
//                         <div class="detail-item">
//                             <div class="detail-label">시장 상태:</div>
//                             <div class="detail-value">${data.marketCondition || '-'}</div>
//                         </div>
//                     </div>
//                     <div class="detail-message">
//                         ${data.message || '분석 메시지 없음'}
//                     </div>
//                 </div>
//             </div>
//         `;
        
//         // 모달 닫기 이벤트
//         const closeBtn = modal.querySelector('.close-modal');
//         if (closeBtn) {
//             closeBtn.addEventListener('click', function() {
//                 modal.remove();
//             });
//         }
        
//         // 모달 외부 클릭 시 닫기
//         modal.addEventListener('click', function(e) {
//             if (e.target === modal) {
//                 modal.remove();
//             }
//         });
        
//         // 모달 표시
//         document.body.appendChild(modal);
//     }
    
//     // 공개 API
//     return {
//         addDataPoint,
//         updateDashboard
//     };
// })();

// // 초기화
// window.DashboardComponent = DashboardComponent; 