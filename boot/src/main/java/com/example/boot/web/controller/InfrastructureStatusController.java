package com.example.boot.web.controller;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.boot.exchange.layer4_distribution.kafka.service.LeaderElectionService;
import com.example.boot.web.dto.StatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InfrastructureStatusController {
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final LeaderElectionService leaderElectionService;
    
    // 마지막 로그 출력 시간을 추적
    private long lastRedisErrorLog = 0;
    private long lastKafkaErrorLog = 0;
    private static final long LOG_INTERVAL = 60000; // 1분

    @GetMapping("/api/v1/trading/mode/status")
    public StatusResponse getStatus() {
        boolean redisOk = false;
        try {
            String pingResult = redisTemplate.execute((RedisCallback<String>) connection -> {
                return new String(connection.ping());
            });
            redisOk = "PONG".equals(pingResult);
        } catch (RedisConnectionFailureException e) {
            // 1분에 한 번만 로그 출력
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastRedisErrorLog > LOG_INTERVAL) {
                log.warn("Redis connection check failed: {}", e.getMessage());
                lastRedisErrorLog = currentTime;
            }
        } catch (Exception e) {
            log.error("Unexpected error during Redis check", e);
        }

        boolean kafkaOk = false;
        try {
            try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                // 1초 타임아웃으로 브로커 노드 목록 조회 시도
                adminClient.describeCluster().nodes().get(1, java.util.concurrent.TimeUnit.SECONDS);
                kafkaOk = true;
            }
        } catch (Exception e) {
            // 1분에 한 번만 로그 출력
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastKafkaErrorLog > LOG_INTERVAL) {
                log.warn("Kafka connection check failed: {}", e.getMessage());
                lastKafkaErrorLog = currentTime;
            }
        }

        return StatusResponse.builder()
            .redisOk(redisOk)
            .kafkaOk(kafkaOk)
            .leaderOk(leaderElectionService.isLeader())
            .valid(redisOk && kafkaOk)
            .build();
    }
} 