package com.example.activityservice.service;

import com.example.activityservice.dto.ActivityRequest;
import com.example.activityservice.dto.ActivityResponse;
import com.example.activityservice.exception.ResourceNotFoundException;
import com.example.activityservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final KafkaTemplate<String,Activity> kafkaTemplate;

    @Value("${kafka.topic.name}")
    private String topicName;

    public ActivityResponse trackActivity(ActivityRequest req) {
        if (!userValidationService.validateUser(req.getUserId())) {
            throw new ResourceNotFoundException("User " + req.getUserId() + " not found");
        }

        Activity activity = Activity.builder()
                .userId(req.getUserId())
                .type(req.getType())
                .duration(req.getDuration())
                .caloriesBurned(req.getCaloriesBurned())
                .startTime(req.getStartTime())
                .additionalMetrics(req.getAdditionalMetrics())
                .build();
        Activity savedActivity = activityRepository.save(activity);
        publishActivityEvent(savedActivity);
        return mapToResponse(savedActivity);
    }

    /**
     * Publishes the event that triggers AI analysis. Sending is asynchronous, so broker
     * failures are reported in the callback; the activity itself is already saved.
     * See specs/002 for the transactional-outbox trade-off.
     */
    private void publishActivityEvent(Activity activity) {
        kafkaTemplate.send(topicName, activity.getUserId(), activity)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish activity {} to topic {}", activity.getId(), topicName, ex);
                    } else {
                        log.info("Published activity {} to {}-{}@{}", activity.getId(), topicName,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }

    private ActivityResponse mapToResponse(Activity savedActivity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(savedActivity.getId());
        response.setUserId(savedActivity.getUserId());
        response.setType(savedActivity.getType());
        response.setStartTime(savedActivity.getStartTime());
        response.setDuration(savedActivity.getDuration());
        response.setCaloriesBurned(savedActivity.getCaloriesBurned());
        response.setAdditionalMetrics(savedActivity.getAdditionalMetrics());
        response.setCreatedAt(savedActivity.getCreatedAt());
        response.setUpdatedAt(savedActivity.getUpdatedAt());
        return response;
    }

    public List<ActivityResponse> getUserActivities(String userId) {
        List<Activity> activityList = activityRepository.findByUserId(userId);
        return activityList.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}
