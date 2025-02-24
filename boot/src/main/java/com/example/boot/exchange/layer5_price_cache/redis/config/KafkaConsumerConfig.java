package com.example.boot.exchange.layer5_price_cache.redis.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, StandardExchangeData> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
        
        JsonDeserializer<StandardExchangeData> jsonDeserializer = new JsonDeserializer<>(StandardExchangeData.class);
        jsonDeserializer.addTrustedPackages("com.example.boot.exchange.layer3_data_converter.model");
        
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StandardExchangeData> kafkaListenerContainerFactory(
            ConsumerFactory<String, StandardExchangeData> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, StandardExchangeData> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setShutdownTimeout(5000L);
        return factory;
    }
} 