package com.example.boot.exchange.layer4_distribution.kafka.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import lombok.extern.slf4j.Slf4j;
import reactor.kafka.receiver.ReceiverOptions;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.topics.trades}")
    private String topic;

    @Bean
    public NewTopic exchangeTopic(@Value("${spring.kafka.topics.trades}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, StandardExchangeData> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, StandardExchangeData> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ReceiverOptions<String, StandardExchangeData> kafkaReceiverOptions() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StandardExchangeData.class.getName());

        return ReceiverOptions.<String, StandardExchangeData>create(consumerProps)
            .subscription(Collections.singleton(topic))
            .addAssignListener(partitions -> log.info("Assigned partitions: {}", partitions))
            .addRevokeListener(partitions -> log.info("Revoked partitions: {}", partitions));
    }
} 