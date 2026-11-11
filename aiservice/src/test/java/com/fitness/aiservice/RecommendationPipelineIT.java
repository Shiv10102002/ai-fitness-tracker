package com.fitness.aiservice;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import com.fitness.aiservice.service.GeminiService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
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
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>("activity-events", "kc-1", event)).get();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Recommendation rec = recommendationRepository.findByActivityIdAndUserId("act-42", "kc-1").orElseThrow();
            assertThat(rec.getRecommendation()).isEqualTo("Overall:Nice ride");
            assertThat(rec.getActivityType()).isEqualTo("CYCLING");
        });
    }
}
