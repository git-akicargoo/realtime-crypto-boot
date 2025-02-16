package com.example.boot.exchange.layer4_distribution.direct.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;
import com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationService;
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
    
    public DirectDistributionService(ExchangeDataIntegrationService integrationService) {
        this.integrationService = integrationService;
        this.clientSinks = new ConcurrentHashMap<>();
        this.isDistributing = new AtomicBoolean(false);
    }
    
    @Override
    public Flux<StandardExchangeData> startDistribution() {
        if (!isDistributing.compareAndSet(false, true)) {
            log.info("Distribution already started");
            return getExistingDistribution();
        }
        
        log.info("Starting direct distribution");
        return integrationService.subscribe()
            .doOnNext(data -> broadcastToClients(data))
            .doOnError(error -> log.error("Error in distribution: ", error))
            .doOnCancel(() -> isDistributing.set(false))
            .doOnComplete(() -> isDistributing.set(false))
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
            clientSinks.clear();
            log.info("Distribution stopped");
        });
    }
    
    @Override
    public boolean isDistributing() {
        return isDistributing.get();
    }
    
    private void broadcastToClients(StandardExchangeData data) {
        clientSinks.forEach((clientId, sink) -> {
            sink.tryEmitNext(data);
        });
    }
    
    private Flux<StandardExchangeData> getExistingDistribution() {
        String clientId = "client-" + System.currentTimeMillis();
        Sinks.Many<StandardExchangeData> sink = Sinks.many().multicast().onBackpressureBuffer();
        clientSinks.put(clientId, sink);
        
        return sink.asFlux()
            .doFinally(signalType -> clientSinks.remove(clientId));
    }
} 