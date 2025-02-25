package com.example.boot.common.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiConfigController {
    
    @Value("${server.scheme:http}")
    private String scheme;
    
    @Value("${server.host:localhost}")
    private String host;
    
    @Value("${server.port}")
    private int port;
    
    @GetMapping("/config")
    public Map<String, String> getApiConfig() {
        return Map.of(
            "baseUrl", String.format("%s://%s:%d", scheme, host, port)
        );
    }
} 