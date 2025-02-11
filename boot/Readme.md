Exchange Service Architecture
Overview
Exchange Service는 다양한 거래소의 실시간 데이터를 수집하고 표준화하여 여러 인프라에 제공하는 서비스입니다.
Architecture
Layer Structure
Layer 1 - Core (/layer1_core/)
기본 모델과 프로토콜을 정의하는 핵심 계층
CurrencyPair: 거래쌍 모델
ExchangeMessage: 기본 메시지 모델
BaseExchangeProtocol: 거래소 프로토콜 인터페이스
거래소별 프로토콜 구현체
Layer 2 - WebSocket (/layer2_websocket/)
거래소 연결 및 실시간 데이터 수신 담당
ConnectionFactory: 웹소켓 연결 생성
MessageHandler: 메시지 송수신 처리
WebSocketConfig: 웹소켓 설정
Layer 3 - Data Converter (/layer3_data_converter/)
거래소별 데이터 표준화 및 변환
StandardExchangeData: 표준화된 데이터 모델
DataConverterService: 데이터 변환 서비스 인터페이스
ExchangeDataConverter: 거래소별 변환 로직
Layer 4 - Service (/layer4_service/)
비즈니스 로직 및 데이터 가공
ExchangeDataProcessor: 공통 데이터 처리
인프라별 특화 서비스:
KafkaExchangeService
WebSocketExchangeService
RedisExchangeService
Infrastructure
Required Infrastructure
항상 활성화되어 있는 필수 인프라
MySQL
기본 데이터 저장소
설정 정보 관리
Web
클라이언트 웹소켓 연동
REST API 제공
Optional Infrastructure
application.yml 설정으로 활성화/비활성화 가능한 선택적 인프라
Kafka: 메시지 브로커
Redis: 캐싱 및 세션 관리
Implementation Status
Completed
[x] Layer 1 Core
[x] CurrencyPair 모델
[x] ExchangeMessage 모델
[x] BaseExchangeProtocol 인터페이스
[x] 거래소별 프로토콜 구현
[x] Layer 2 WebSocket
[x] WebSocketConfig 설정
[x] ConnectionFactory 구현
[x] MessageHandler 구현
In Progress
[ ] Layer 3 Data Converter
[ ] StandardExchangeData 모델
[ ] DataConverterService 인터페이스
[ ] 거래소별 변환 로직
[ ] 테스트 코드
Planned
[ ] Layer 4 Service
[ ] Infrastructure Integration
[ ] Integration Tests
Data Flow
1. Exchange -> Layer 2 (WebSocket)
Layer 2 -> Layer 3 (Data Converter)
Layer 3 -> Layer 4 (Service)
Layer 4 -> Infrastructure
Key Considerations
1. Layer Design
각 레이어의 명확한 책임과 역할 정의
레이어간 의존성 방향 준수
인터페이스 기반 설계
2. Infrastructure Independence
필수 인프라 (MySQL, Web)
핵심 기능 구현에 필요한 최소 인프라
항상 활성화
선택적 인프라 (Kafka, Redis)
application.yml 설정으로 활성화/비활성화
비활성화되어도 코어 기능 영향 없음
@ConditionalOnProperty 사용
3. Extensibility
새로운 거래소 추가 용이
새로운 인프라 추가 용이
인터페이스 기반 설계로 구현체 교체 용이
4. Maintainability
명확한 패키지 구조
테스트 용이성
문서화
5. Performance
비동기 처리
캐싱 전략
배치 처리 고려