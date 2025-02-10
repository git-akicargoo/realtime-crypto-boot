package com.example.boot.exchange_layer.layer5_message_handler;

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;

import reactor.core.publisher.Mono;

public interface MessageHandler {
    Mono<NormalizedMessage> handleMessage(String message);
    boolean supports(String exchange);
} 