package com.example.boot.exchange.layer4_distribution.kafka.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.monitoring.DataFlowMonitor;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;
import com.example.boot.exchange.layer4_distribution.kafka.health.KafkaHealthIndicator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
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

    @PostConstruct
    public void init() {
        try {
            log.info("🚀 Initializing KafkaDistributionService\n" +
                     "├─ Role: {}\n" +
                     "└─ Topic: {}", 
                     leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER",
                     topic);
            
            if (!healthIndicator.isAvailable()) {
                log.error("❌ Kafka is not available");
                return;
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
    }

    @Override
    public Flux<StandardExchangeData> startDistribution() {
        if (sharedFlux == null) {
            log.error("❌ Distribution flux not initialized!");
            return Flux.empty();
        }
        return sharedFlux;
    }

    private Flux<StandardExchangeData> createDistributionFlux() {
        String role = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
        log.debug("Creating distribution flux - Role: {}", role);
        
        Flux<StandardExchangeData> producerFlux = leaderElectionService.isLeader()
            ? integrationService.subscribe()
                .doOnSubscribe(s -> log.info("👑 [LEADER] Starting exchange data subscription"))
                .doOnNext(data -> {
                    log.debug("📊 [LEADER] Exchange data: {}", data);
                    dataFlowMonitor.incrementExchangeData();
                    kafkaTemplate.send(topic, data.getExchange(), data)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                dataFlowMonitor.incrementKafkaSent();
                                log.debug("📤 [LEADER] Kafka message sent");
                            }
                        });
                })
            : Flux.empty();

        Flux<StandardExchangeData> consumerFlux = kafkaReceiver.receive()
            .doOnSubscribe(s -> log.info("📥 [{}] Starting Kafka message consumption", role))
            .doOnNext(record -> {
                log.debug("📥 [{}] Kafka message received: {}", role, record.value());
                dataFlowMonitor.incrementKafkaReceived();
                broadcastToClients(record.value());
            })
            .map(record -> record.value());

        return Flux.merge(producerFlux, consumerFlux);
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
            isDistributing.set(false);
            distributionStatus.setDistributing(false);
            clientSinks.clear();
            log.info("Distribution stopped");
        });
    }

    @Override
    public boolean isDistributing() {
        return isDistributing.get();
    }

    private void broadcastToClients(StandardExchangeData data) {
        String nodeRole = leaderElectionService.isLeader() ? "LEADER" : "FOLLOWER";
        
        log.debug("🔄 Broadcasting to clients\n" +
                 "├─ Role: {}\n" +
                 "├─ Active Clients: {}\n" +
                 "├─ Exchange: {}\n" +
                 "└─ Data: {}", 
                 nodeRole,
                 clientSinks.size(),
                 data.getExchange(),
                 data);

        if (clientSinks.isEmpty()) {
            log.debug("⚠️ No active clients to broadcast to");
            return;
        }

        clientSinks.forEach((clientId, sink) -> {
            Sinks.EmitResult result = sink.tryEmitNext(data);
            if (result.isSuccess()) {
                log.debug("✅ Successfully sent to client: {}", clientId);
                dataFlowMonitor.incrementClientSent();
            } else {
                log.error("❌ Failed to send to client: {} (Result: {})", clientId, result);
            }
        });
    }
} 