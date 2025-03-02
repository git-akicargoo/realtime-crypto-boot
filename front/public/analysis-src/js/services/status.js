// 시스템 상태 확인 모듈
const StatusService = (function() {
    // 시스템 상태 확인
    async function checkSystemStatus() {
        try {
            // 경로 수정: /api/v1/status -> /api/v1/trading/mode/status
            const response = await fetch('/api/v1/trading/mode/status');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            
            console.log('시스템 상태 응답:', data);
            
            // 백엔드 응답 구조에 맞게 변환 (변환 필요 없음 - 이미 일치함)
            return {
                redisOk: data.redisOk,
                kafkaOk: data.kafkaOk,
                leaderOk: data.leaderOk,
                valid: data.valid
            };
        } catch (error) {
            console.warn('시스템 상태 확인 중 오류:', error);
            
            // 개발/테스트 환경에서는 오류 무시하고 진행 (선택 사항)
            if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
                console.log('개발 환경에서는 상태 오류 무시하고 진행합니다.');
                return {
                    redisOk: true,
                    kafkaOk: true,
                    leaderOk: true,
                    valid: true
                };
            }
            
            return {
                redisOk: false,
                kafkaOk: false,
                leaderOk: false,
                valid: false
            };
        }
    }
    
    // 상태 확인 및 알림
    async function validateSystemStatus() {
        const status = await checkSystemStatus();
        console.log('시스템 상태:', status);
        
        if (!status.valid) {
            let errorMessage = '시스템 상태에 문제가 있습니다:';
            if (!status.redisOk) errorMessage += ' Redis 연결 실패,';
            if (!status.kafkaOk) errorMessage += ' Kafka 연결 실패,';
            if (!status.leaderOk) errorMessage += ' 리더 노드가 아님,';
            
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
    
    // 공개 API
    return {
        checkSystemStatus,
        validateSystemStatus,
        showStatusAlert
    };
})(); 