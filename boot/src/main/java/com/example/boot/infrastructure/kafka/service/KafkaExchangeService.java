package com.example.boot.infrastructure.kafka.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class KafkaExchangeService {

    private final KafkaTemplate<String, StandardExchangeData> kafkaTemplate;
    private final ConsumerFactory<String, StandardExchangeData> consumerFactory;
    private final Map<String, Sinks.Many<StandardExchangeData>> sinkMap = new ConcurrentHashMap<>();
    private final AtomicBoolean kafkaAvailable = new AtomicBoolean(false);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.trades}")
    private String tradesTopic;

    @PostConstruct
    public void init() {
        log.info("Initializing Kafka Exchange Service with bootstrap servers: {}", bootstrapServers);
        checkKafkaAvailability();
    }

    @Scheduled(fixedRate = 10000)
    public void checkKafkaAvailability() {
        try {
            try (Consumer<String, StandardExchangeData> consumer = consumerFactory.createConsumer("health-check-consumer")) {
                consumer.listTopics(Duration.ofSeconds(3));
                kafkaAvailable.set(true);
                log.info("Kafka Health Check - Config: [{}], Broker: Running, Connection: Connected", 
                         bootstrapServers);
            }
        } catch (Exception e) {
            kafkaAvailable.set(false);
            log.info("Kafka Health Check - Config: [{}], Broker: Not Running, Connection: Disconnected", 
                     bootstrapServers);
        }
    }

    // 거래소 데이터를 Kafka로 발행
    public Mono<SendResult<String, StandardExchangeData>> publishData(StandardExchangeData data) {
        if (!kafkaAvailable.get()) {
            return Mono.error(new IllegalStateException("Kafka is not available"));
        }
        return Mono.fromFuture(kafkaTemplate.send(tradesTopic, data.getExchange(), data))
                  .doOnSuccess(result -> log.debug("Published to Kafka: {}", data))
                  .doOnError(error -> log.error("Failed to publish to Kafka: {}", error.getMessage()));
    }

    // Kafka로부터 데이터 구독
    public Flux<StandardExchangeData> subscribeToData() {
        String sinkKey = Thread.currentThread().getName();
        return Flux.defer(() -> {
            Sinks.Many<StandardExchangeData> sink = sinkMap.computeIfAbsent(
                sinkKey,
                k -> Sinks.many().multicast().onBackpressureBuffer()
            );
            return sink.asFlux();
        });
    }

    // Kafka Listener
    @KafkaListener(topics = "${app.kafka.topic.trades}", 
                  groupId = "${spring.kafka.consumer.group-id}",
                  autoStartup = "false")  // 자동 시작 비활성화
    private void handleKafkaMessage(StandardExchangeData data) {
        if (kafkaAvailable.get()) {
            log.debug("Received from Kafka: {}", data);
            sinkMap.values().forEach(sink -> 
                sink.tryEmitNext(data)
                    .orThrow()
            );
        }
    }

    // 구독 해제
    public void unsubscribe(String sinkKey) {
        Sinks.Many<StandardExchangeData> sink = sinkMap.remove(sinkKey);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    // Kafka 가용성 상태 확인
    public boolean isKafkaAvailable() {
        return kafkaAvailable.get();
    }
} 