package com.example.boot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaConfig {
    
    @Bean
    public NewTopic leaderTopic() {
        return TopicBuilder.name("exchange.leader")
                          .partitions(1)
                          .replicas(1)
                          .build();
    }

    @Bean
    public NewTopic dataTopic() {
        return TopicBuilder.name("exchange.data")
                          .partitions(1)
                          .replicas(1)
                          .build();
    }
} 