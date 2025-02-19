package com.example.boot.exchange.layer4_distribution.kafka.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.common.logging.ScheduledLogger;
import com.example.boot.common.session.registry.SessionRegistry;
import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
import com.example.boot.exchange.layer4_distribution.common.event.LeaderElectionEvent;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.monitoring.DataFlowMonitor;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

@Slf4j
@Service
public class KafkaDistributionService implements DistributionService {
    private final ExchangeDataIntegrationService integrationService;
    private final KafkaTemplate<String, StandardExchangeData> kafkaTemplate;
    private final KafkaReceiver<String, StandardExchangeData> kafkaReceiver;
    private final KafkaHealthIndicator healthIndicator;
    private final String topic;
    public final ConcurrentHashMap<String, Sinks.Many<StandardExchangeData>> clientSinks;
    private final AtomicBoolean isDistributing;
    private final LeaderElectionService leaderElectionService;
    private final DistributionStatus distributionStatus;
    private final DataFlowMonitor dataFlowMonitor;
    private final ScheduledLogger scheduledLogger;
    private volatile Flux<StandardExchangeData> sharedFlux;
    private final SessionRegistry sessionRegistry;
    private volatile Disposable disposable;

    public KafkaDistributionService(
        ExchangeDataIntegrationService integrationService,
        KafkaTemplate<String, StandardExchangeData> kafkaTemplate,
        ReceiverOptions<String, StandardExchangeData> receiverOptions,
        KafkaHealthIndicator healthIndicator,
        LeaderElectionService leaderElectionService,
        @Value("${spring.kafka.topics.trades}") String topic,
        DistributionStatus distributionStatus,
        DataFlowMonitor dataFlowMonitor,
        ScheduledLogger scheduledLogger,
        SessionRegistry sessionRegistry
    ) {
        this.integrationService = integrationService;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaReceiver = KafkaReceiver.create(receiverOptions);
        this.healthIndicator = healthIndicator;
        this.topic = topic;
        this.clientSinks = new ConcurrentHashMap<>();
        this.isDistributing = new AtomicBoolean(false);
        this.leaderElectionService = leaderElectionService;
        this.distributionStatus = distributionStatus;
        this.dataFlowMonitor = dataFlowMonitor;
        this.scheduledLogger = scheduledLogger;
        this.sessionRegistry = sessionRegistry;
        log.info("Initialized Kafka distribution service with topic: {}", topic);
    }

    @Override
    public Flux<StandardExchangeData> startDistribution() {
        if (!healthIndicator.isAvailable()) {
            log.error("❌ Cannot start: Kafka is not available");
            return Flux.empty();
        }
        
        try {
            log.info("🚀 Initializing KafkaDistributionService");
            
            // 초기화 시점에 Kafka 연결 상태 확인
            if (!healthIndicator.isAvailable()) {
                log.error("❌ Kafka is not available");
                isDistributing.set(false);
                distributionStatus.setDistributing(false);
                return Flux.empty();
            }

            // createDistributionFlux() 사용
            this.sharedFlux = createDistributionFlux()
                .doOnSubscribe(s -> {
                    log.info("✅ Distribution flux started");
                    isDistributing.set(true);
                    distributionStatus.setDistributing(true);
                })
                .doOnNext(data -> {
                    String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
                    log.debug("📊 Processing data [{}]: {}", role, data);
                })
                .share();

            // 초기 구독 시작
            this.sharedFlux.subscribe();
            
            log.info("✅ Distribution service initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize", e);
            throw new RuntimeException("Failed to initialize", e);
        }

        return sharedFlux;
    }

    private Flux<StandardExchangeData> createDistributionFlux() {
        if (!healthIndicator.isAvailable()) {
            log.warn("Kafka is not available, not creating distribution flux");
            return Flux.empty();
        }

        String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
        scheduledLogger.scheduleLog(log, "Creating distribution flux - Role: {}", role);
        
        if (leaderElectionService.isLeader()) {
            return integrationService.subscribe()
                .distinct()
                .filter(data -> isDistributing() && healthIndicator.isAvailable())
                .doOnNext(data -> {
                    if (!healthIndicator.isAvailable()) {
                        log.debug("Skipping message: Kafka not available");
                        return;
                    }
                    try {
                        scheduledLogger.scheduleLog(log, "📤 [LEADER] Processing messages - Exchange: {}, Price: {}", 
                            data.getExchange(), data.getPrice());
                        kafkaTemplate.send(topic, data.getExchange(), data)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.debug("Message queued but Kafka unavailable - Exchange: {}", data.getExchange());
                                } else {
                                    dataFlowMonitor.incrementKafkaSent();
                                }
                            });
                    } catch (Exception e) {
                        log.debug("Unable to process message while Kafka unavailable - Exchange: {}", data.getExchange());
                    }
                });
        }

        return kafkaReceiver.receive()
            .filter(record -> isDistributing() && healthIndicator.isAvailable())
            .map(record -> {
                StandardExchangeData data = record.value();
                scheduledLogger.scheduleLog(log, "📥 [{}] Processing messages - Exchange: {}, Price: {}", 
                    role, data.getExchange(), data.getPrice());
                dataFlowMonitor.incrementKafkaReceived();
                return data;
            })
            .doOnNext(this::broadcastToClients);
    }

    @Override
    public Mono<Void> sendToClient(String clientId, StandardExchangeData data) {
        Sinks.Many<StandardExchangeData> sink = clientSinks.get(clientId);
        if (sink != null) {
            return Mono.fromRunnable(() -> 
                sink.tryEmitNext(data)
            );
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> stopDistribution() {
        return Mono.fromRunnable(() -> {
            // clientSinks를 clear하지 않도록 수정
            isDistributing.set(false);
            distributionStatus.setDistributing(false);
            if (disposable != null) {
                disposable.dispose();
            }
            log.info("Kafka distribution service stopped");
        });
    }

    @Override
    public boolean isDistributing() {
        return isDistributing.get();
    }

    @Override
    public Map<String, Sinks.Many<StandardExchangeData>> getActiveSinks() {
        return new HashMap<>(clientSinks);
    }

    @Override
    public void restoreSinks(Map<String, Sinks.Many<StandardExchangeData>> sinks) {
        clientSinks.clear();
        clientSinks.putAll(sinks);
        log.info("Restored {} client sinks", sinks.size());
    }

    private void broadcastToClients(StandardExchangeData data) {
        int clientCount = clientSinks.size();
        if (clientCount > 0) {
            clientSinks.forEach((clientId, sink) -> {
                // 세션이 유효한 경우에만 데이터 전송
                if (sessionRegistry.getSession(clientId) != null) {
                    boolean success = sink.tryEmitNext(data).isSuccess();
                    if (success) {
                        dataFlowMonitor.incrementClientSent();
                        log.debug("📨 Sent to client {}: Exchange={}, Price={}", 
                            clientId, data.getExchange(), data.getPrice());
                    }
                } else {
                    // 유효하지 않은 세션의 Sink 제거
                    clientSinks.remove(clientId);
                    log.debug("Removed invalid client sink: {}", clientId);
                }
            });
            scheduledLogger.scheduleLog(log, "📢 Active clients: {}", clientCount);
        } else {
            log.debug("📢 No clients connected to broadcast to");
        }
    }

    @Scheduled(fixedRateString = "${infrastructure.health-check.interval:10000}")
    public void checkAndReconnect() {
        if (!healthIndicator.isAvailable() && isDistributing.get()) {
            log.warn("⚠️ Kafka connection lost, attempting to reconnect...");
            stopDistribution()
                .then(Mono.defer(() -> {
                    if (healthIndicator.isAvailable()) {
                        log.info("✅ Kafka is available again, restarting distribution");
                        return startDistribution().then();
                    }
                    return Mono.empty();
                }))
                .subscribe();
        }
    }

    // LeaderElection 이벤트 발생 시
    @EventListener
    public void handleLeaderElection(LeaderElectionEvent event) {
        if (event.isLeader()) {
            // 리더가 될 때도 Kafka 상태 확인
            if (!healthIndicator.isAvailable()) {
                log.error("❌ Cannot start as leader: Kafka is not available");
                return;
            }
            // 리더 역할 시작
            startDistribution().subscribe();
        }
    }

    public void addClientSink(String clientId, Sinks.Many<StandardExchangeData> sink) {
        clientSinks.put(clientId, sink);
        log.info("Added client sink for client ID: {}", clientId);
    }
} 