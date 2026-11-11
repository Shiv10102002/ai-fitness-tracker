package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendationService recommendationService;

    @Test
    void userRecommendations_areScopedToCaller() throws Exception {
        when(recommendationService.getUserRecommendations("alice-sub"))
                .thenReturn(List.of(Recommendation.builder().activityId("act-1").userId("alice-sub").build()));

        mockMvc.perform(get("/api/recommendation/user").header("X-User-ID", "alice-sub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activityId").value("act-1"));
    }

    @Test
    void activityRecommendation_ofAnotherUser_isNotFound() throws Exception {
        when(recommendationService.getActivityRecommendation("bobs-activity", "alice-sub"))
                .thenThrow(new RuntimeException("No recommendation found"));

        mockMvc.perform(get("/api/recommendation/activity/bobs-activity").header("X-User-ID", "alice-sub"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsWithoutGatewayIdentity_areRejected() throws Exception {
        mockMvc.perform(get("/api/recommendation/user"))
                .andExpect(status().isBadRequest());
    }
}
