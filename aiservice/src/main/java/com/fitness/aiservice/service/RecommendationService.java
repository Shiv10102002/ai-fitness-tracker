package com.fitness.aiservice.service;

import com.fitness.aiservice.exception.ResourceNotFoundException;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository recommendationRepository;

    public List<Recommendation> getUserRecommendations(String userId) {
        return recommendationRepository.findByUserId(userId);
    }

    /**
     * Returns the caller's recommendation for an activity. A recommendation owned by another
     * user is reported as "not found" so its existence is not leaked.
     */
    public Recommendation getActivityRecommendation(String activityId, String userId) {
        return recommendationRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No recommendation found for activity " + activityId));
    }
}
