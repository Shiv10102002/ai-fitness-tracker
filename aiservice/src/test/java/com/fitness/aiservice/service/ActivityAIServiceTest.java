package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.ActivityType;
import com.fitness.aiservice.model.Recommendation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityAIServiceTest {

    @Mock
    GeminiService geminiService;

    @InjectMocks
    ActivityAIService activityAIService;

    @Test
    void generateRecommendation_parsesFencedJsonFromGemini() throws IOException {
        when(geminiService.getRecommendations(anyString())).thenReturn(fixture("gemini/valid-response.json"));

        Recommendation rec = activityAIService.generateRecommendation(activity());

        assertThat(rec.getActivityId()).isEqualTo("act-1");
        assertThat(rec.getUserId()).isEqualTo("kc-1");
        assertThat(rec.getActivityType()).isEqualTo("RUNNING");
        assertThat(rec.getRecommendation())
                .contains("Overall:Solid steady-state run.")
                .contains("Heart Rate:Zone 2 most of the time.");
        assertThat(rec.getImprovements()).containsExactly("Cadence: Aim for 170 spm.");
        assertThat(rec.getSuggestions()).containsExactly("Intervals: 6 x 400m at 5k pace.");
        assertThat(rec.getSafety()).containsExactly("Warm up for 10 minutes", "Hydrate");
    }

    @Test
    void generateRecommendation_includesActivityDetailsInPrompt() throws IOException {
        when(geminiService.getRecommendations(anyString())).thenReturn(fixture("gemini/valid-response.json"));

        activityAIService.generateRecommendation(activity());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiService).getRecommendations(prompt.capture());
        assertThat(prompt.getValue())
                .contains("Activity Type: RUNNING")
                .contains("Duration: 30 minutes")
                .contains("Calories Burned: 300")
                .contains("distanceKm");
    }

    @Test
    void generateRecommendation_fallsBackToDefault_whenGeminiReturnsNonJsonText() {
        when(geminiService.getRecommendations(anyString())).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"Sorry, I cannot help with that."}]}}]}
                """);

        Recommendation rec = activityAIService.generateRecommendation(activity());

        assertThat(rec.getRecommendation()).isEqualTo("Unable to generate detailed analysis");
        assertThat(rec.getActivityId()).isEqualTo("act-1");
    }

    @Test
    void generateRecommendation_usesPlaceholders_whenSectionsAreMissing() {
        when(geminiService.getRecommendations(anyString())).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"analysis\\":{\\"overall\\":\\"Good\\"}}"}]}}]}
                """);

        Recommendation rec = activityAIService.generateRecommendation(activity());

        assertThat(rec.getRecommendation()).isEqualTo("Overall:Good");
        assertThat(rec.getImprovements()).containsExactly("No Specific improvements provided");
        assertThat(rec.getSuggestions()).containsExactly("No Specific suggestions provided");
        assertThat(rec.getSafety()).containsExactly("Follow general safety guidelines");
    }

    static Activity activity() {
        return Activity.builder()
                .id("act-1")
                .userId("kc-1")
                .type(ActivityType.RUNNING)
                .duration(30)
                .caloriesBurned(300)
                .additionalMetrics(Map.of("distanceKm", 5.2))
                .build();
    }

    static String fixture(String path) throws IOException {
        try (InputStream in = ActivityAIServiceTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
