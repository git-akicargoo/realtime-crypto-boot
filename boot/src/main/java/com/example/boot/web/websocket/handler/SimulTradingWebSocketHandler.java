package com.example.boot.web.websocket.handler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.boot.exchange.layer7_trading.dto.SimulTradingRequest;
import com.example.boot.exchange.layer7_trading.dto.SimulTradingResponse;
import com.example.boot.exchange.layer7_trading.event.SimulTradingUpdateEvent;
import com.example.boot.exchange.layer7_trading.service.SimulTradingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulTradingWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
    private final SimulTradingService simulTradingService;
    private final Map<WebSocketSession, String> sessionMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("모의거래 웹소켓 연결 성립: {}", session.getId());
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // 메시지 파싱
            Map<String, Object> requestMap = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) requestMap.get("action");
            
            if (action == null) {
                sendErrorMessage(session, "Action is required");
                return;
            }
            
            // 액션에 따른 처리
            switch (action) {
                case "startSimulTrading":
                    handleStartSimulTrading(session, requestMap);
                    break;
                case "stopSimulTrading":
                    handleStopSimulTrading(session);
                    break;
                default:
                    sendErrorMessage(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("모의거래 웹소켓 메시지 처리 오류", e);
            sendErrorMessage(session, "Error processing message: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("모의거래 웹소켓 연결 종료: {}", session.getId());
        
        // 모의거래 중지
        handleStopSimulTrading(session);
        
        // 세션 정보 제거
        sessionMap.remove(session);
    }
    
    /**
     * 모의거래 시작 요청 처리
     * @param session 웹소켓 세션
     * @param requestMap 요청 데이터
     */
    private void handleStartSimulTrading(WebSocketSession session, Map<String, Object> requestMap) {
        try {
            // 이미 진행 중인 모의거래가 있는지 확인
            if (sessionMap.containsKey(session)) {
                // 기존 모의거래 중지
                handleStopSimulTrading(session);
            }
            
            // 요청 데이터 변환
            SimulTradingRequest request = objectMapper.convertValue(requestMap, SimulTradingRequest.class);
            
            // 모의거래 시작
            SimulTradingResponse response = simulTradingService.startSimulTrading(request);
            
            // 세션 정보 저장
            sessionMap.put(session, response.getSessionId());
            
            // 응답 전송
            sendMessage(session, response);
            
            log.info("모의거래 시작: 세션={}, 모의거래 세션={}", session.getId(), response.getSessionId());
        } catch (Exception e) {
            log.error("모의거래 시작 오류", e);
            sendErrorMessage(session, "Failed to start simulated trading: " + e.getMessage());
        }
    }
    
    /**
     * 모의거래 중지 요청 처리
     * @param session 웹소켓 세션
     */
    private void handleStopSimulTrading(WebSocketSession session) {
        try {
            String simulSessionId = sessionMap.get(session);
            if (simulSessionId != null) {
                // 모의거래 중지
                SimulTradingResponse response = simulTradingService.stopSimulTrading(simulSessionId);
                
                // 응답 전송
                sendMessage(session, response);
                
                // 세션 정보 제거
                sessionMap.remove(session);
                
                log.info("모의거래 중지: 세션={}, 모의거래 세션={}", session.getId(), simulSessionId);
            }
        } catch (Exception e) {
            log.error("모의거래 중지 오류", e);
            sendErrorMessage(session, "Failed to stop simulated trading: " + e.getMessage());
        }
    }
    
    /**
     * 웹소켓 메시지 전송
     * @param session 웹소켓 세션
     * @param data 전송할 데이터
     */
    public void sendMessage(WebSocketSession session, Object data) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 오류", e);
        } catch (IOException e) {
            log.error("웹소켓 메시지 전송 오류", e);
        }
    }
    
    /**
     * 오류 메시지 전송
     * @param session 웹소켓 세션
     * @param errorMessage 오류 메시지
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            if (session.isOpen()) {
                Map<String, Object> errorResponse = Map.of(
                    "type", "error",
                    "message", errorMessage
                );
                String jsonMessage = objectMapper.writeValueAsString(errorResponse);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (Exception e) {
            log.error("오류 메시지 전송 실패", e);
        }
    }
    
    /**
     * 모의거래 업데이트 이벤트 처리
     * @param event 모의거래 업데이트 이벤트
     */
    @EventListener
    public void handleSimulTradingUpdateEvent(SimulTradingUpdateEvent event) {
        // 이벤트에 해당하는 세션 찾기
        sessionMap.forEach((session, sessionId) -> {
            if (sessionId.equals(event.getSessionId())) {
                // 업데이트 메시지 전송
                sendMessage(session, event.getResponse());
            }
        });
    }
} 