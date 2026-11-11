package com.fitness.aiservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumer error handling (specs/002, M2): a failing record is retried twice, one second
 * apart, then published to {@code <topic>.DLT} so it no longer blocks its partition.
 * Records that could not be deserialized at all arrive as raw bytes and are forwarded as-is.
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    public static final String DLT_SUFFIX = ".DLT";

    /**
     * Producer for dead letters. Bootstrap servers come from {@link KafkaConnectionDetails}, not
     * {@link KafkaProperties}, so the broker resolved by Boot (properties, Docker Compose or
     * Testcontainers) is used, the same one the consumer reads from.
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties properties,
                                                                 KafkaConnectionDetails connectionDetails) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties());
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(Object.class, new JacksonJsonSerializer<>());

        DefaultKafkaProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(config);
        producerFactory.setValueSerializer(new DelegatingByTypeSerializer(byType, true));
        KafkaTemplate<Object, Object> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        // Explicit destination: spring-kafka 4 defaults to a "-dlt" suffix; we document ".DLT".
        // A negative partition lets the producer choose, so the DLT may have any partition count.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Retry {} for record {}-{}@{}: {}", attempt, record.topic(), record.partition(),
                        record.offset(), ex.getMessage()));
        return handler;
    }
}
