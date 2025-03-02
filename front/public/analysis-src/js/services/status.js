// 시스템 상태 확인 모듈
const StatusService = (function() {
    // 시스템 상태 확인
    async function checkSystemStatus() {
        try {
            console.log('시스템 상태 확인 시작...');
            const response = await fetch('/api/v1/trading/mode/status');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            
            console.log('시스템 상태 응답:', data);
            
            return {
                redisOk: data.redisOk,
                kafkaOk: data.kafkaOk,
                leaderOk: data.leaderOk,
                valid: data.redisOk && data.kafkaOk
            };
        } catch (error) {
            console.warn('시스템 상태 확인 중 오류:', error);
            
            return {
                redisOk: false,
                kafkaOk: false,
                leaderOk: false,
                valid: false
            };
        }
    }
    
    // 시스템 상태 오버레이 표시
    function showSystemBlockOverlay(errorMessage) {
        console.log('showSystemBlockOverlay 실행:', errorMessage);
        
        // 기존 오버레이 제거
        removeSystemBlockOverlay();
        
        // 오버레이 생성
        const overlay = document.createElement('div');
        overlay.id = 'systemBlockOverlay';
        
        // 오버레이 스타일
        Object.assign(overlay.style, {
            position: 'fixed',
            top: '0',
            left: '0',
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: '10000',
            color: '#fff',
            textAlign: 'center',
            padding: '20px'
        });
        
        // 메시지 및 내용 추가
        overlay.innerHTML = `
            <div class="system-error-icon" style="font-size: 48px; margin-bottom: 20px;">⚠️</div>
            <h2 style="margin-bottom: 20px;">시스템 연결 오류</h2>
            <p style="margin-bottom: 30px; max-width: 600px;">${errorMessage}</p>
            <p style="margin-bottom: 20px;">현재 페이지를 이용하려면 Redis와 Kafka 서비스를 기동해주세요.</p>
            <button id="retrySystemCheck" class="retry-button" style="padding: 10px 20px; background: #f1c40f; color: #333; border: none; border-radius: 4px; cursor: pointer;">
                상태 다시 확인
            </button>
        `;
        
        console.log('오버레이 DOM에 추가 전');
        document.body.appendChild(overlay);
        console.log('오버레이 DOM에 추가 완료');
        
        // 재시도 버튼 이벤트
        document.getElementById('retrySystemCheck').addEventListener('click', async function() {
            console.log('재시도 버튼 클릭됨');
            
            // 버튼 스타일 변경 (클릭 확인용)
            this.textContent = '확인 중...';
            this.style.backgroundColor = '#ccc';
            this.disabled = true;
            
            const status = await checkSystemStatus();
            if (status.valid) {
                removeSystemBlockOverlay();
            } else {
                // 상태가 여전히 좋지 않으면 메시지 갱신
                let newErrorMessage = '현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다:';
                if (!status.redisOk) newErrorMessage += ' Redis 연결 실패,';
                if (!status.kafkaOk) newErrorMessage += ' Kafka 연결 실패,';
                
                showSystemBlockOverlay(newErrorMessage.slice(0, -1));
                
                // 버튼 클릭 후 오류 상태가 계속되면 추가 안내 메시지 표시
                const errorAlert = document.createElement('div');
                errorAlert.style.backgroundColor = '#e74c3c';
                errorAlert.style.padding = '10px 20px';
                errorAlert.style.borderRadius = '5px';
                errorAlert.style.marginTop = '20px';
                errorAlert.style.maxWidth = '600px';
                errorAlert.style.textAlign = 'center';
                errorAlert.textContent = '시스템이 아직 준비되지 않았습니다. 서비스 관리자에게 문의하세요.';
                
                // 기존 알림이 있으면 제거
                const existingAlert = document.querySelector('#systemBlockOverlay .error-alert');
                if (existingAlert) {
                    existingAlert.remove();
                }
                
                errorAlert.className = 'error-alert';
                document.querySelector('#systemBlockOverlay').appendChild(errorAlert);
                
                // 5초 후 알림 제거
                setTimeout(() => {
                    if (errorAlert.parentNode) {
                        errorAlert.remove();
                    }
                }, 5000);
            }
        });
        
        // 스크롤 방지
        document.body.style.overflow = 'hidden';
        console.log('오버레이 표시 완료');
    }
    
    // 시스템 차단 오버레이 제거
    function removeSystemBlockOverlay() {
        const overlay = document.getElementById('systemBlockOverlay');
        if (overlay) {
            overlay.remove();
            document.body.style.overflow = '';
        }
    }
    
    // 상태 확인 및 페이지 블록
    async function validateAndBlockIfNeeded() {
        console.log('validateAndBlockIfNeeded 실행 시작');
        const status = await checkSystemStatus();
        console.log('상태 확인 결과:', status);
        
        if (!status.valid) {
            console.log('상태가 유효하지 않음, 오버레이 표시 시도');
            let errorMessage = '현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다:';
            if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
            if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
            
            console.log('표시할 오류 메시지:', errorMessage);
            showSystemBlockOverlay(errorMessage.slice(0, -1));
            return false;
        }
        
        console.log('시스템 상태 정상, 페이지 차단 없음');
        return true;
    }
    
    // 상태 확인 및 알림
    async function validateSystemStatus() {
        const status = await checkSystemStatus();
        console.log('시스템 상태:', status);
        
        if (!status.valid) {
            let errorMessage = '시스템 상태에 문제가 있습니다:';
            if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
            if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
            
            // 알림 표시
            showStatusAlert(errorMessage.slice(0, -1));
            return false;
        }
        
        return true;
    }
    
    // 상태 알림 표시
    function showStatusAlert(message) {
        // 기존 알림이 있으면 제거
        const existingAlert = document.querySelector('.status-alert');
        if (existingAlert) {
            existingAlert.remove();
        }
        
        // 새 알림 생성
        const alert = document.createElement('div');
        alert.className = 'status-alert';
        alert.innerHTML = `
            <span class="alert-message">${message}</span>
            <button class="alert-close">×</button>
        `;
        
        // 알림 스타일
        Object.assign(alert.style, {
            position: 'fixed',
            top: '20px',
            right: '20px',
            backgroundColor: 'var(--danger-color)',
            color: 'white',
            padding: '15px',
            borderRadius: '5px',
            boxShadow: '0 3px 10px rgba(0,0,0,0.2)',
            zIndex: '1000',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            maxWidth: '400px'
        });
        
        // 닫기 버튼
        const closeBtn = alert.querySelector('.alert-close');
        closeBtn.addEventListener('click', () => alert.remove());
        
        // 자동 닫기 (10초 후)
        setTimeout(() => {
            if (document.body.contains(alert)) {
                alert.remove();
            }
        }, 10000);
        
        document.body.appendChild(alert);
    }
    
    // 기존 시스템 블록 오버레이 업데이트
    function updateSystemBlockOverlay(systemStatus) {
        const overlay = document.getElementById('system-block-overlay');
        if (!overlay) return;
        
        // Redis와 Kafka 상태만으로 시스템 사용 가능 여부 결정
        const isSystemValid = systemStatus.redisOk && systemStatus.kafkaOk;
        
        if (!isSystemValid) {
            // 시스템을 사용할 수 없는 경우
            let errorMessages = [];
            
            if (!systemStatus.redisOk) {
                errorMessages.push('Redis 연결 실패');
            }
            
            if (!systemStatus.kafkaOk) {
                errorMessages.push('Kafka 연결 실패');
            }
            
            const errorMessageText = errorMessages.length > 0 
                ? ': ' + errorMessages.join(', ')
                : '';
                
            overlay.innerHTML = `
                <div class="system-block-content">
                    <h2>시스템 연결 오류</h2>
                    <p>현재 시스템을 사용할 수 없습니다. 시스템 조건이 만족되지 않습니다${errorMessageText}</p>
                    
                    <div class="system-status">
                        <p class="${systemStatus.redisOk ? 'status-ok' : 'status-error'}">
                            ${systemStatus.redisOk ? '✅' : '❌'}<br>
                            Redis 연결: ${systemStatus.redisOk ? '정상' : '실패'}
                        </p>
                        <p class="${systemStatus.kafkaOk ? 'status-ok' : 'status-error'}">
                            ${systemStatus.kafkaOk ? '✅' : '❌'}<br>
                            Kafka 연결: ${systemStatus.kafkaOk ? '정상' : '실패'}
                        </p>
                    </div>
                    
                    <p>현재 페이지를 이용하려면 모든 서비스가 정상 작동해야 합니다.</p>
                </div>
            `;
            overlay.style.display = 'flex';
            console.log('updateSystemBlockOverlay 실행:', overlay.textContent.trim());
        } else {
            // 시스템을 사용할 수 있는 경우 오버레이 숨기기
            overlay.style.display = 'none';
        }
    }
    
    // 공개 API
    return {
        checkSystemStatus,
        validateSystemStatus,
        showStatusAlert,
        showSystemBlockOverlay,
        updateSystemBlockOverlay,
        removeSystemBlockOverlay,
        validateAndBlockIfNeeded
    };
})();

// 중요: 전역 객체로 등록 (페이지가 로드될 때 사용 가능하도록)
window.StatusService = StatusService; 