package com.example.activityservice.service;

import com.example.activityservice.dto.ActivityRequest;
import com.example.activityservice.dto.ActivityResponse;
import com.example.activityservice.model.Activity;
import com.example.activityservice.model.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    ActivityRepository activityRepository;
    @Mock
    UserValidationService userValidationService;
    @Mock
    KafkaTemplate<String, Activity> kafkaTemplate;

    ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, userValidationService, kafkaTemplate);
        ReflectionTestUtils.setField(activityService, "topicName", "activity-events");
    }

    @Test
    void trackActivity_savesActivityAndPublishesEventKeyedByUser() {
        when(userValidationService.validateUser("kc-1")).thenReturn(true);
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId("act-1");
            return a;
        });

        ActivityResponse response = activityService.trackActivity(request("kc-1"));

        assertThat(response.getId()).isEqualTo("act-1");
        assertThat(response.getUserId()).isEqualTo("kc-1");
        assertThat(response.getType()).isEqualTo(ActivityType.RUNNING);
        verify(kafkaTemplate).send(eq("activity-events"), eq("kc-1"), argThat(a -> "act-1".equals(a.getId())));
    }

    @Test
    void trackActivity_rejectsUnknownUser_andDoesNotPersistOrPublish() {
        when(userValidationService.validateUser("ghost")).thenReturn(false);

        assertThatThrownBy(() -> activityService.trackActivity(request("ghost")))
                .hasMessageContaining("ghost");

        verifyNoInteractions(activityRepository, kafkaTemplate);
    }

    @Test
    void getUserActivities_mapsEntitiesToResponses() {
        Activity a = Activity.builder().id("act-1").userId("kc-1").type(ActivityType.YOGA).duration(45).build();
        when(activityRepository.findByUserId("kc-1")).thenReturn(List.of(a));

        List<ActivityResponse> result = activityService.getUserActivities("kc-1");

        assertThat(result).singleElement()
                .satisfies(r -> {
                    assertThat(r.getId()).isEqualTo("act-1");
                    assertThat(r.getType()).isEqualTo(ActivityType.YOGA);
                    assertThat(r.getDuration()).isEqualTo(45);
                });
        verify(activityRepository, never()).findAll();
        verify(activityRepository).findByUserId(anyString());
    }

    static ActivityRequest request(String userId) {
        ActivityRequest req = new ActivityRequest();
        req.setUserId(userId);
        req.setType(ActivityType.RUNNING);
        req.setDuration(30);
        req.setCaloriesBurned(300);
        return req;
    }
}
