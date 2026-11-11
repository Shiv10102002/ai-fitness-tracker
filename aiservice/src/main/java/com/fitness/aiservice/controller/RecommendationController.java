package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code X-User-ID} is set by the gateway from the verified JWT (specs/001-identity-from-jwt.md). */
@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping("/user")
    public ResponseEntity<List<Recommendation>> getUserRecommendations(@RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(recommendationService.getUserRecommendations(userId));
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId,
                                                                    @RequestHeader("X-User-ID") String userId) {
        try {
            return ResponseEntity.ok(recommendationService.getActivityRecommendation(activityId, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
