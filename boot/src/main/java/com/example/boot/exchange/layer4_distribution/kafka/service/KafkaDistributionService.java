package com.example.boot.exchange.layer4_distribution.kafka.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
import com.example.boot.exchange.layer4_distribution.common.event.LeaderElectionEvent;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.monitoring.DataFlowMonitor;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;

import lombok.extern.slf4j.Slf4j;
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
    private final ConcurrentHashMap<String, Sinks.Many<StandardExchangeData>> clientSinks;
    private final AtomicBoolean isDistributing;
    private final LeaderElectionService leaderElectionService;
    private final DistributionStatus distributionStatus;
    private final DataFlowMonitor dataFlowMonitor;
    private volatile Flux<StandardExchangeData> sharedFlux;

    public KafkaDistributionService(
        ExchangeDataIntegrationService integrationService,
        KafkaTemplate<String, StandardExchangeData> kafkaTemplate,
        ReceiverOptions<String, StandardExchangeData> receiverOptions,
        KafkaHealthIndicator healthIndicator,
        LeaderElectionService leaderElectionService,
        @Value("${spring.kafka.topics.trades}") String topic,
        DistributionStatus distributionStatus,
        DataFlowMonitor dataFlowMonitor
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
        log.info("Creating distribution flux - Role: {}", role);
        
        if (leaderElectionService.isLeader()) {
            return integrationService.subscribe()
                .distinct()
                .filter(data -> isDistributing() && healthIndicator.isAvailable())
                .doOnNext(data -> {
                    if (!healthIndicator.isAvailable()) {
                        log.warn("Kafka is not available, skipping message");
                        return;
                    }
                    try {
                        log.info("📤 [LEADER] Sending to Kafka - Exchange: {}, Price: {}", 
                            data.getExchange(), data.getPrice());
                        kafkaTemplate.send(topic, data.getExchange(), data)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.error("Failed to send message to Kafka", ex);
                                } else {
                                    dataFlowMonitor.incrementKafkaSent();
                                }
                            });
                    } catch (Exception e) {
                        log.error("Error sending to Kafka", e);
                    }
                });
        }

        // 팔로워 로직은 그대로 유지
        return kafkaReceiver.receive()
            .filter(record -> isDistributing() && healthIndicator.isAvailable())
            .map(record -> {
                StandardExchangeData data = record.value();
                log.info("📥 [{}] Received from Kafka - Exchange: {}, Price: {}", 
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
            log.info("Stopping Kafka distribution service");
            isDistributing.set(false);
            distributionStatus.setDistributing(false);
            
            // 기존 Flux 구독 취소
            if (sharedFlux != null) {
                sharedFlux = null;
            }
            
            clientSinks.clear();
            log.info("Kafka distribution service stopped");
        }).then();
    }

    @Override
    public boolean isDistributing() {
        return isDistributing.get();
    }

    private void broadcastToClients(StandardExchangeData data) {
        int clientCount = clientSinks.size();
        if (clientCount > 0) {
            clientSinks.forEach((clientId, sink) -> {
                sink.tryEmitNext(data);
                dataFlowMonitor.incrementClientSent();
                log.debug("📨 Sent to client {}: Exchange={}, Price={}", 
                    clientId, data.getExchange(), data.getPrice());
            });
            log.info("📢 Broadcasted to {} clients", clientCount);
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
} 