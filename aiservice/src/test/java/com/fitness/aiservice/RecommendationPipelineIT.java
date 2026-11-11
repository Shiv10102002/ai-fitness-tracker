package com.fitness.aiservice;

import com.fitness.aiservice.repository.RecommendationRepository;
import com.fitness.aiservice.service.GeminiService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publishes an activity event to real Kafka and verifies a recommendation is stored in
 * real MongoDB. Gemini is mocked. Requires Docker.
 */
@SpringBootTest
@Testcontainers
class RecommendationPipelineIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @MockitoBean
    GeminiService geminiService;

    @Autowired
    RecommendationRepository recommendationRepository;

    @Test
    void activityEvent_resultsInStoredRecommendation() throws Exception {
        when(geminiService.getRecommendations(anyString())).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"analysis\\":{\\"overall\\":\\"Nice ride\\"}}"}]}}]}
                """);

        String event = """
                {"id":"act-42","userId":"kc-1","type":"CYCLING","duration":60,"caloriesBurned":500}
                """;
        send(event);

        // Must fail with an AssertionError while the record is missing: untilAsserted only
        // retries on AssertionError, so e.g. Optional.orElseThrow() would abort the wait.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(recommendationRepository.findByActivityIdAndUserId("act-42", "kc-1"))
                        .hasValueSatisfying(rec -> {
                            assertThat(rec.getRecommendation()).isEqualTo("Overall:Nice ride");
                            assertThat(rec.getActivityType()).isEqualTo("CYCLING");
                        }));
    }

    @Test
    void redeliveredEvent_doesNotCreateSecondRecommendation() throws Exception {
        when(geminiService.getRecommendations(anyString())).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}
                """);
        String event = """
                {"id":"act-dup","userId":"kc-1","type":"YOGA","duration":20,"caloriesBurned":80}
                """;

        send(event);
        send(event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(recommendationRepository.findByUserId("kc-1"))
                        .filteredOn(r -> "act-dup".equals(r.getActivityId())).hasSize(1));
        // Give the second delivery time to be processed, then confirm Gemini was called once.
        Thread.sleep(2000);
        verify(geminiService, times(1)).getRecommendations(org.mockito.ArgumentMatchers.contains("YOGA"));
    }

    @Test
    void malformedEvent_isRoutedToDeadLetterTopic() throws Exception {
        send("this is not json");

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-inspector",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
            consumer.subscribe(List.of("activity-events.DLT"));
            ConsumerRecord<String, byte[]> dead =
                    KafkaTestUtils.getSingleRecord(consumer, "activity-events.DLT", Duration.ofSeconds(30));

            assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo("this is not json");
        }
    }

    private static void send(String value) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>("activity-events", "kc-1", value)).get();
        }
    }
}
