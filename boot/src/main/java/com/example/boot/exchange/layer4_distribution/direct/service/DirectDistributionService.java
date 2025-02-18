package com.example.boot.exchange.layer4_distribution.direct.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
import com.example.boot.exchange.layer4_distribution.common.health.DistributionStatus;
import com.example.boot.exchange.layer4_distribution.common.monitoring.DataFlowMonitor;
import com.example.boot.exchange.layer4_distribution.common.service.DistributionService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class DirectDistributionService implements DistributionService {
    private final ExchangeDataIntegrationService integrationService;
    private final ConcurrentHashMap<String, Sinks.Many<StandardExchangeData>> clientSinks;
    private final AtomicBoolean isDistributing;
    private final DistributionStatus distributionStatus;
    private final DataFlowMonitor dataFlowMonitor;
    
    public DirectDistributionService(
        ExchangeDataIntegrationService integrationService,
        DistributionStatus distributionStatus,
        DataFlowMonitor dataFlowMonitor
    ) {
        this.integrationService = integrationService;
        this.clientSinks = new ConcurrentHashMap<>();
        this.isDistributing = new AtomicBoolean(false);
        this.distributionStatus = distributionStatus;
        this.dataFlowMonitor = dataFlowMonitor;
    }
    
    @Override
    public Flux<StandardExchangeData> startDistribution() {
        if (!isDistributing.compareAndSet(false, true)) {
            log.info("Distribution already running, returning existing distribution");
            return getExistingDistribution();
        }
        
        log.info("🚀 Starting direct distribution");
        distributionStatus.setDistributing(true);
        
        return integrationService.subscribe()
            .doOnSubscribe(subscription -> {
                log.info("✅ Direct distribution subscribed and active");
            })
            .doOnNext(data -> {
                dataFlowMonitor.incrementExchangeData();
                log.debug("📥 Received from exchange: {}", data.getExchange());
                broadcastToClients(data);
            })
            .doOnError(error -> {
                log.error("❌ Error in distribution: ", error);
                isDistributing.set(false);
                distributionStatus.setDistributing(false);
            })
            .doOnCancel(() -> {
                log.info("Distribution cancelled");
                isDistributing.set(false);
                distributionStatus.setDistributing(false);
            })
            .doOnComplete(() -> {
                log.info("Distribution completed");
                isDistributing.set(false);
                distributionStatus.setDistributing(false);
            })
            .share();
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
        int clientCount = clientSinks.size();
        if (clientCount > 0) {
            clientSinks.forEach((clientId, sink) -> {
                boolean success = sink.tryEmitNext(data).isSuccess();
                if (success) {
                    dataFlowMonitor.incrementClientSent();
                    log.debug("📨 Sent to client {}: Exchange={}, Price={}", 
                        clientId, data.getExchange(), data.getPrice());
                }
            });
            log.debug("📢 Broadcasted to {} clients", clientCount);
        }
    }
    
    private Flux<StandardExchangeData> getExistingDistribution() {
        String clientId = "client-" + System.currentTimeMillis();
        Sinks.Many<StandardExchangeData> sink = Sinks.many().multicast().onBackpressureBuffer();
        clientSinks.put(clientId, sink);
        
        return sink.asFlux()
            .doFinally(signalType -> clientSinks.remove(clientId));
    }
} 