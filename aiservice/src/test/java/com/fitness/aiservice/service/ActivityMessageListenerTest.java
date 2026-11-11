package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityMessageListenerTest {

    @Mock
    ActivityAIService activityAIService;
    @Mock
    RecommendationRepository recommendationRepository;

    @InjectMocks
    ActivityMessageListener listener;

    @Test
    void savesGeneratedRecommendation() {
        Activity activity = ActivityAIServiceTest.activity();
        Recommendation rec = Recommendation.builder().activityId("act-1").build();
        when(activityAIService.generateRecommendation(activity)).thenReturn(rec);

        listener.processActivity(activity);

        verify(recommendationRepository).save(rec);
    }

    @Test
    void savesFallbackRecommendation_whenGeminiCallFails() {
        Activity activity = ActivityAIServiceTest.activity();
        Recommendation fallback = Recommendation.builder().activityId("act-1").recommendation("fallback").build();
        when(activityAIService.generateRecommendation(activity)).thenThrow(new RuntimeException("503 from Gemini"));
        when(activityAIService.generateFallbackRecommendation(activity)).thenReturn(fallback);

        listener.processActivity(activity);

        verify(recommendationRepository).save(fallback);
    }
}
