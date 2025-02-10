package com.example.boot.exchange_layer.layer6_message_broker.config;

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

import com.example.boot.exchange_layer.layer5_message_handler.model.NormalizedMessage;

@Configuration
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Bean
    public ConsumerFactory<String, NormalizedMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        JsonDeserializer<NormalizedMessage> jsonDeserializer = new JsonDeserializer<>(NormalizedMessage.class);
        jsonDeserializer.addTrustedPackages("com.example.boot.exchange_layer.layer5_message_handler.model");
        
        return new DefaultKafkaConsumerFactory<>(
            config,
            new StringDeserializer(),
            jsonDeserializer
        );
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NormalizedMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, NormalizedMessage> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
} 