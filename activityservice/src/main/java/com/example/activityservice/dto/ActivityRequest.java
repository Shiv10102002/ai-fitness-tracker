package com.example.activityservice.dto;

import com.example.activityservice.model.ActivityType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ActivityRequest {
    private String userId;
    private ActivityType type;
    private Integer duration;
    private LocalDateTime startTime;
    private Integer caloriesBurned;
    private Map<String,Object> additionalMetrics;
}
