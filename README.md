# 실시간 암호화폐 분석 시스템

## 프로젝트 개요
실시간 암호화폐 분석 시스템은 다양한 거래소의 실시간 데이터를 수집하고 분석하여 사용자에게 실시간 시장 분석 정보를 제공하는 시스템입니다.

### 주요 기능
- 실시간 암호화폐 가격 데이터 수집 및 분석
- WebSocket을 통한 실시간 데이터 전송
- 기술적 지표 기반의 매매 신호 생성
- 모의 거래 기능
- 반응형 웹 인터페이스

## 시스템 요구사항
- Java 17 이상
- Node.js 16 이상
- Docker & Docker Compose
- Redis (선택사항)
- Kafka (선택사항)

## 실행 방법

### 1. 로컬 환경에서 실행

#### 백엔드 실행
```bash
# 프로젝트 루트 디렉토리에서
cd boot
./gradlew bootRun
```

#### 프론트엔드 실행
```bash
# 프로젝트 루트 디렉토리에서
cd front
npm install
npm run dev
```

### 2. Docker 환경에서 실행

#### Docker Compose로 전체 시스템 실행
```bash
# 프로젝트 루트 디렉토리에서
docker-compose up -d
```

#### 개별 서비스 실행
```bash
# 백엔드만 실행
docker-compose up -d backend

# 프론트엔드만 실행
docker-compose up -d frontend
```

## 접속 방법
- 프론트엔드: http://localhost:3000
- 백엔드 API: http://localhost:8080
- WebSocket: ws://localhost:8080/ws/stomp/analysis

## 주요 기능 설명

### 1. 실시간 데이터 분석
- 거래소별 실시간 가격 데이터 수집
- 기술적 지표 계산 (RSI, SMA 등)
- 매매 신호 생성

### 2. 모의 거래
- 매매 신호 기반 자동 거래
- 거래 이력 관리
- 성과 분석

### 3. 모니터링 대시보드
- 실시간 포지션 현황
- 거래 이력 조회
- 성과 분석 차트

## 시스템 아키텍처

### 백엔드
- Spring Boot 기반 REST API
- WebSocket을 통한 실시간 데이터 전송
- Redis를 이용한 데이터 캐싱
- Kafka를 통한 메시지 큐잉 (선택사항)

### 프론트엔드
- 반응형 웹 인터페이스
- WebSocket을 통한 실시간 데이터 수신
- TradingView 차트 통합
- 모의 거래 대시보드

## 개발 환경 설정

### 백엔드 설정
```yaml
# application.yml
spring:
  profiles:
    active: local
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

### 프론트엔드 설정
```javascript
// config.js
const config = {
  apiUrl: 'http://localhost:8080',
  wsUrl: 'ws://localhost:8080/ws/stomp/analysis'
};
```

## 문제 해결
- Redis 연결 오류: Redis 서버가 실행 중인지 확인
- Kafka 연결 오류: Kafka 서버가 실행 중인지 확인
- WebSocket 연결 오류: 백엔드 서버가 실행 중인지 확인

## 라이선스
이 프로젝트는 MIT 라이선스를 따릅니다. 