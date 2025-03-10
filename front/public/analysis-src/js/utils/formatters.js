// 포맷 유틸리티
const FormatUtils = (function() {
    // 숫자 포맷
    function formatNumber(value, decimals = 2) {
        if (value === undefined || value === null) return '-';
        return Number(value).toLocaleString(undefined, {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        });
    }
    
    // 가격 포맷 (작은 값은 더 많은 소수점 표시)
    function formatPrice(price) {
        if (price === undefined || price === null) return '-';
        
        const numPrice = Number(price);
        
        if (isNaN(numPrice)) return '-';
        
        if (numPrice === 0) return '0';
        
        // 가격 범위에 따라 소수점 자릿수 조정
        if (numPrice < 0.00001) return numPrice.toFixed(8);
        if (numPrice < 0.0001) return numPrice.toFixed(7);
        if (numPrice < 0.001) return numPrice.toFixed(6);
        if (numPrice < 0.01) return numPrice.toFixed(5);
        if (numPrice < 0.1) return numPrice.toFixed(4);
        if (numPrice < 1) return numPrice.toFixed(3);
        if (numPrice < 10) return numPrice.toFixed(2);
        if (numPrice < 1000) return numPrice.toFixed(1);
        
        return numPrice.toLocaleString(undefined, {maximumFractionDigits: 0});
    }
    
    // 퍼센트 포맷
    function formatPercent(value) {
        if (value === undefined || value === null) return '-';
        
        const numValue = Number(value);
        
        if (isNaN(numValue)) return '-';
        
        const formattedValue = numValue.toFixed(1);
        return `${formattedValue}%`;
    }
    
    // 날짜/시간 포맷
    function formatDateTime(dateTimeString) {
        if (!dateTimeString) return '-';
        
        try {
            const date = new Date(dateTimeString);
            
            return date.toLocaleString(undefined, {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: false
            });
        } catch (error) {
            console.error('날짜 형식 변환 중 오류:', error);
            return dateTimeString;
        }
    }
    
    // 시간만 포맷
    function formatTime(dateTimeString) {
        if (!dateTimeString) return '-';
        
        try {
            const date = new Date(dateTimeString);
            
            return date.toLocaleTimeString(undefined, {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: false
            });
        } catch (error) {
            console.error('시간 형식 변환 중 오류:', error);
            return '-';
        }
    }
    
    // 짧은 ID 생성 (8자)
    function generateShortId() {
        return Math.random().toString(16).substring(2, 10);
    }
    
    // 공개 API
    return {
        formatNumber,
        formatPrice,
        formatPercent,
        formatDateTime,
        formatTime,
        generateShortId
    };
})(); 