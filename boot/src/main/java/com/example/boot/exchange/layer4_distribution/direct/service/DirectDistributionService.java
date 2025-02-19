package com.example.boot.exchange.layer4_distribution.direct.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.example.boot.common.session.registry.SessionRegistry;
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
    private final SessionRegistry sessionRegistry;
    
    public DirectDistributionService(
        ExchangeDataIntegrationService integrationService,
        DistributionStatus distributionStatus,
        DataFlowMonitor dataFlowMonitor,
        SessionRegistry sessionRegistry
    ) {
        this.integrationService = integrationService;
        this.clientSinks = new ConcurrentHashMap<>();
        this.isDistributing = new AtomicBoolean(false);
        this.distributionStatus = distributionStatus;
        this.dataFlowMonitor = dataFlowMonitor;
        this.sessionRegistry = sessionRegistry;
    }
    
    @Override
    public Flux<StandardExchangeData> startDistribution() {
        if (!isDistributing.compareAndSet(false, true)) {
            log.info("Distribution already running");
            return Flux.empty();  // 이미 실행 중이면 빈 Flux 반환
        }
        
        log.info("🚀 Starting direct distribution");
        distributionStatus.setDistributing(true);
        
        return integrationService.subscribe()
            .doOnNext(data -> {
                dataFlowMonitor.incrementExchangeData();
                broadcastToClients(data);
            })
            .doOnError(e -> {
                log.error("Error in distribution: ", e);
                isDistributing.set(false);
                distributionStatus.setDistributing(false);
            });
    }
    
    @Override
    public Mono<Void> sendToClient(String clientId, StandardExchangeData data) {
        Sinks.Many<StandardExchangeData> sink = clientSinks.get(clientId);
        if (sink != null && sessionRegistry.getSession(clientId) != null) {
            boolean success = sink.tryEmitNext(data).isSuccess();
            if (success) {
                dataFlowMonitor.incrementClientSent();
                log.debug("📨 Sent to client {}: Exchange={}, Price={}", 
                    clientId, data.getExchange(), data.getPrice());
            }
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
                if (sessionRegistry.getSession(clientId) != null) {
                    boolean success = sink.tryEmitNext(data).isSuccess();
                    if (success) {
                        dataFlowMonitor.incrementClientSent();
                        log.debug("📨 Sent to client {}: Exchange={}, Price={}", 
                            clientId, data.getExchange(), data.getPrice());
                    }
                } else {
                    clientSinks.remove(clientId);
                    log.debug("Removed invalid client sink: {}", clientId);
                }
            });
            log.debug("📢 Broadcasted to {} clients", clientCount);
        }
    }

    public void addClientSink(String clientId, Sinks.Many<StandardExchangeData> sink) {
        clientSinks.put(clientId, sink);
        log.info("Added client sink for client ID: {}", clientId);
    }
} 