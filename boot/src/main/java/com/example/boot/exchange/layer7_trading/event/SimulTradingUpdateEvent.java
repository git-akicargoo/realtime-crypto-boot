package com.example.boot.exchange.layer7_trading.event;

import org.springframework.context.ApplicationEvent;

import com.example.boot.exchange.layer7_trading.dto.SimulTradingResponse;

/**
 * 모의거래 상태 업데이트 이벤트
 */
public class SimulTradingUpdateEvent extends ApplicationEvent {
    
    private static final long serialVersionUID = 1L;
    
    private final String sessionId;
    private final SimulTradingResponse response;
    
    public SimulTradingUpdateEvent(Object source, String sessionId, SimulTradingResponse response) {
        super(source);
        this.sessionId = sessionId;
        this.response = response;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public SimulTradingResponse getResponse() {
        return response;
    }
} 