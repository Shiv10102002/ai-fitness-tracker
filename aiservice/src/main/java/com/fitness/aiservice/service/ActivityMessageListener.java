package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes activity events and stores one recommendation per activity.
 *
 * <p>Kafka delivers at-least-once, so the same event can arrive twice (e.g. after a
 * rebalance). Processing is idempotent (specs/002, M3): an existing recommendation is
 * detected up front, and the unique index on {@code activityId} catches the race where two
 * deliveries are processed concurrently. Gemini failures fall back to a default
 * recommendation; anything else propagates to the error handler (retry, then DLT).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;
    private final RecommendationRepository recommendationRepository;

    @KafkaListener(topics = "${kafka.topic.name}", groupId = "activity-processor-group")
    public void processActivity(Activity activity) {
        if (recommendationRepository.existsByActivityId(activity.getId())) {
            log.info("Recommendation for activity {} already exists, skipping duplicate event", activity.getId());
            return;
        }
        log.info("Generating recommendation for activity {} (user {})", activity.getId(), activity.getUserId());

        Recommendation recommendation;
        try {
            recommendation = activityAIService.generateRecommendation(activity);
        } catch (RuntimeException e) {
            log.error("AI recommendation failed for activity {}, storing fallback", activity.getId(), e);
            recommendation = activityAIService.generateFallbackRecommendation(activity);
        }

        try {
            recommendationRepository.save(recommendation);
        } catch (DuplicateKeyException e) {
            log.info("Recommendation for activity {} was stored concurrently, ignoring duplicate", activity.getId());
        }
    }
}
