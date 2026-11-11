package com.example.activityservice.dto;

import com.example.activityservice.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ActivityRequest {
    /** Ignored if sent by the client: always overwritten with the gateway's X-User-ID. */
    private String userId;

    @NotNull(message = "Activity type is required")
    private ActivityType type;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0 minutes")
    private Integer duration;

    @PastOrPresent(message = "Start time cannot be in the future")
    private LocalDateTime startTime;

    @PositiveOrZero(message = "Calories burned cannot be negative")
    private Integer caloriesBurned;

    private Map<String, Object> additionalMetrics;
}
