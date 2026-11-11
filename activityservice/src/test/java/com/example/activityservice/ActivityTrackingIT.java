package com.example.activityservice;

import com.example.activityservice.dto.ActivityRequest;
import com.example.activityservice.dto.ActivityResponse;
import com.example.activityservice.model.ActivityType;
import com.example.activityservice.service.ActivityRepository;
import com.example.activityservice.service.ActivityService;
import com.example.activityservice.service.UserValidationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tracks an activity against real MongoDB and Kafka (Testcontainers) and verifies the
 * event that ai-service consumes is published. Requires Docker.
 */
@SpringBootTest
@Testcontainers
class ActivityTrackingIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @MockitoBean
    UserValidationService userValidationService;

    @Autowired
    ActivityService activityService;

    @Autowired
    ActivityRepository activityRepository;

    @Test
    void trackedActivity_isPersisted_andPublishedToKafka() {
        when(userValidationService.validateUser("kc-1")).thenReturn(true);
        ActivityRequest req = new ActivityRequest();
        req.setUserId("kc-1");
        req.setType(ActivityType.CYCLING);
        req.setDuration(60);
        req.setCaloriesBurned(500);
        req.setAdditionalMetrics(Map.of("distanceKm", 20));

        ActivityResponse saved = activityService.trackActivity(req);

        assertThat(activityRepository.findByUserId("kc-1")).extracting("id").containsExactly(saved.getId());

        try (Consumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("activity-events"));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "activity-events", Duration.ofSeconds(30));

            assertThat(record.key()).isEqualTo("kc-1");
            assertThat(record.value()).contains(saved.getId()).contains("CYCLING");
        }
    }
}
