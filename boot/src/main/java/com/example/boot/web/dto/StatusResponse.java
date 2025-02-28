package com.example.boot.web.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StatusResponse {
    private boolean redisOk;
    private boolean kafkaOk;
    private boolean leaderOk;
    private boolean valid;
} 