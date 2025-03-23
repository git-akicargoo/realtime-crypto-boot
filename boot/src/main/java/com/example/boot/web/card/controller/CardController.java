package com.example.boot.web.card.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer6_analysis.dto.AnalysisRequest;
import com.example.boot.web.card.service.CardService;
import com.example.boot.web.controller.InfrastructureStatusController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카드 컨트롤러
 * 카드 관리를 위한 REST API를 제공합니다.
 * CRUD 패턴을 따르는 RESTful API를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {
    
    private final CardService cardService;
    private final InfrastructureStatusController infraStatus;
    
    /**
     * 카드 생성 (선택적으로 분석 시작)
     * 
     * @param request 분석 요청 객체
     * @param startAnalysis 분석 시작 여부 (기본값: false)
     * @param httpServletRequest HTTP 요청 정보
     * @return 생성된 카드
     */
    @PostMapping
    public ResponseEntity<?> createCard(
            @RequestBody AnalysisRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean startAnalysis,
            HttpServletRequest httpServletRequest) {
        
        log.info("Received card creation request: {}, startAnalysis: {}", request, startAnalysis);
        
        // 인프라 상태 확인
        var status = infraStatus.getStatus();
        if (!status.isValid()) {
            log.warn("Infrastructure not ready, status: {}", status);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Trading infrastructure is not ready. Please try again later.");
        }
        
        try {
            // 세션 ID 생성
            String sessionId = httpServletRequest.getSession().getId();
            
            // 카드 생성 (필요시 분석 시작)
            AnalysisRequest card = cardService.createCard(request, sessionId, startAnalysis);
            
            log.info("Card created: {}, analysis started: {}", card.getCardId(), startAnalysis);
            return ResponseEntity.ok(card);
        } catch (Exception e) {
            log.error("Error creating card: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating card: " + e.getMessage());
        }
    }
    
    /**
     * 카드 상태 업데이트 (분석 시작/중지 등)
     * 
     * @param cardId 카드 ID
     * @param request 업데이트 요청 (status 필드 포함)
     * @param httpServletRequest HTTP 요청 정보
     * @return 업데이트된 카드
     */
    @PutMapping("/{cardId}")
    public ResponseEntity<?> updateCard(
            @PathVariable String cardId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpServletRequest) {
        
        log.info("Received card update request for cardId: {}, request: {}", cardId, request);
        
        String status = request.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body("Status field is required");
        }
        
        try {
            // 세션 ID 생성
            String sessionId = httpServletRequest.getSession().getId();
            
            // 카드 상태 업데이트
            AnalysisRequest updatedCard = cardService.updateCardStatus(cardId, status, sessionId);
            if (updatedCard == null) {
                return ResponseEntity.notFound().build();
            }
            
            log.info("Card updated: {}, new status: {}", cardId, status);
            return ResponseEntity.ok(updatedCard);
        } catch (Exception e) {
            log.error("Error updating card: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating card: " + e.getMessage());
        }
    }
    
    /**
     * 모든 카드 조회
     * 
     * @return 카드 목록
     */
    @GetMapping
    public ResponseEntity<List<AnalysisRequest>> getAllCards() {
        List<AnalysisRequest> cards = cardService.getAllCards();
        return ResponseEntity.ok(cards);
    }
    
    /**
     * 특정 카드 조회 (웹소켓 연결 정보 포함)
     * 
     * @param cardId 카드 ID
     * @return 카드 정보 및 웹소켓 연결 정보
     */
    @GetMapping("/{cardId}")
    public ResponseEntity<?> getCard(@PathVariable String cardId) {
        Map<String, Object> cardWithWsInfo = cardService.getCardWithWebSocketInfo(cardId);
        if (cardWithWsInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(cardWithWsInfo);
    }
    
    /**
     * 카드 삭제 (분석 중지 포함)
     * 
     * @param cardId 카드 ID
     * @return 삭제 결과
     */
    @DeleteMapping("/{cardId}")
    public ResponseEntity<?> deleteCard(@PathVariable String cardId) {
        log.info("Deleting card: {}", cardId);
        
        boolean deleted = cardService.deleteCard(cardId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok().body("Card deleted successfully");
    }
}
