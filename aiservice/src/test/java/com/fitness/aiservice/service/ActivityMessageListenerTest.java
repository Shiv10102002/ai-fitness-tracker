package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityMessageListenerTest {

    @Mock
    ActivityAIService activityAIService;
    @Mock
    RecommendationRepository recommendationRepository;

    @InjectMocks
    ActivityMessageListener listener;

    final Activity activity = ActivityAIServiceTest.activity();

    @Test
    void savesGeneratedRecommendation() {
        Recommendation rec = Recommendation.builder().activityId("act-1").build();
        when(activityAIService.generateRecommendation(activity)).thenReturn(rec);

        listener.processActivity(activity);

        verify(recommendationRepository).save(rec);
    }

    @Test
    void savesFallbackRecommendation_whenGeminiCallFails() {
        Recommendation fallback = Recommendation.builder().activityId("act-1").recommendation("fallback").build();
        when(activityAIService.generateRecommendation(activity)).thenThrow(new RuntimeException("503 from Gemini"));
        when(activityAIService.generateFallbackRecommendation(activity)).thenReturn(fallback);

        listener.processActivity(activity);

        verify(recommendationRepository).save(fallback);
    }

    @Test
    void duplicateEvent_isSkipped_withoutCallingGemini() {
        when(recommendationRepository.existsByActivityId("act-1")).thenReturn(true);

        listener.processActivity(activity);

        verifyNoInteractions(activityAIService);
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void concurrentDuplicate_rejectedByUniqueIndex_isIgnored() {
        when(activityAIService.generateRecommendation(activity)).thenReturn(Recommendation.builder().build());
        when(recommendationRepository.save(any())).thenThrow(new DuplicateKeyException("E11000"));

        assertThatCode(() -> listener.processActivity(activity)).doesNotThrowAnyException();
    }

    @Test
    void databaseFailure_propagates_soErrorHandlerCanRetryAndDeadLetter() {
        when(activityAIService.generateRecommendation(activity)).thenReturn(Recommendation.builder().build());
        when(recommendationRepository.save(any())).thenThrow(new DataAccessResourceFailureException("Mongo down"));

        assertThatThrownBy(() -> listener.processActivity(activity))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
