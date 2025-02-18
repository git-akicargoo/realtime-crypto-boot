package com.example.boot.exchange.layer4_distribution.kafka.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.example.boot.exchange.layer3_data_converter.model.StandardExchangeData;

import lombok.extern.slf4j.Slf4j;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.topics.trades}")
    private String topic;

    @Value("${spring.kafka.admin.operation-timeout}")
    private String operationTimeout;

    @Value("${spring.kafka.admin.close-timeout}")
    private String closeTimeout;

    @Bean
    public ProducerFactory<String, StandardExchangeData> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 재시도 관련 설정 추가
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
        config.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "1000");
        config.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "5000");
        
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
        
        // 타임아웃 설정 조정
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "3000");
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        consumerProps.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000");
        
        // 재시도 관련 설정 추가
        consumerProps.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");  // 재시도 간격
        consumerProps.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, "1000");  // 재연결 간격
        consumerProps.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "5000");  // 최대 재연결 간격

        return ReceiverOptions.<String, StandardExchangeData>create(consumerProps)
            .subscription(Collections.singletonList(topic))
            .withKeyDeserializer(new StringDeserializer())
            .withValueDeserializer(new JsonDeserializer<>(StandardExchangeData.class));
    }

    @Bean
    public KafkaReceiver<String, StandardExchangeData> kafkaReceiver(
        ReceiverOptions<String, StandardExchangeData> receiverOptions
    ) {
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public NewTopic exchangeTopic() {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, operationTimeout);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, closeTimeout);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setAutoCreate(false);
        return admin;
    }
} 