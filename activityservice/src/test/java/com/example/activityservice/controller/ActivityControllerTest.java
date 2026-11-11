package com.example.activityservice.controller;

import com.example.activityservice.dto.ActivityRequest;
import com.example.activityservice.dto.ActivityResponse;
import com.example.activityservice.exception.ResourceNotFoundException;
import com.example.activityservice.exception.ServiceUnavailableException;
import com.example.activityservice.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ActivityService activityService;

    @Test
    void trackActivity_usesGatewayIdentity_notUserIdFromBody() throws Exception {
        when(activityService.trackActivity(any())).thenReturn(new ActivityResponse());

        mockMvc.perform(post("/api/activities")
                        .header("X-User-ID", "alice-sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"victim-sub","type":"RUNNING","duration":30,"caloriesBurned":300}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ActivityRequest> captor = ArgumentCaptor.forClass(ActivityRequest.class);
        verify(activityService).trackActivity(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("alice-sub");
    }

    @Test
    void getActivities_returnsOnlyCallersActivities() throws Exception {
        ActivityResponse r = new ActivityResponse();
        r.setId("act-1");
        when(activityService.getUserActivities("alice-sub")).thenReturn(List.of(r));

        mockMvc.perform(get("/api/activities").header("X-User-ID", "alice-sub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("act-1"));
    }

    @Test
    void requestsWithoutGatewayIdentity_areRejected() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidActivity_isRejectedWithFieldErrors_andNotProcessed() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .header("X-User-ID", "alice-sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duration":-5,"caloriesBurned":-1,"startTime":"2999-01-01T00:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.type").value("Activity type is required"))
                .andExpect(jsonPath("$.errors.duration").value("Duration must be greater than 0 minutes"))
                .andExpect(jsonPath("$.errors.caloriesBurned").exists())
                .andExpect(jsonPath("$.errors.startTime").exists());

        verify(activityService, never()).trackActivity(any());
    }

    @Test
    void unknownUser_returns404ProblemDetail() throws Exception {
        when(activityService.trackActivity(any())).thenThrow(new ResourceNotFoundException("User ghost not found"));

        mockMvc.perform(post("/api/activities")
                        .header("X-User-ID", "ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User ghost not found"));
    }

    @Test
    void userServiceOutage_returns503() throws Exception {
        when(activityService.trackActivity(any()))
                .thenThrow(new ServiceUnavailableException("user-service is unavailable", new RuntimeException()));

        mockMvc.perform(post("/api/activities")
                        .header("X-User-ID", "alice-sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unexpectedError_returns500WithoutLeakingInternals() throws Exception {
        when(activityService.getUserActivities("alice-sub")).thenThrow(new IllegalStateException("Mongo password=secret"));

        mockMvc.perform(get("/api/activities").header("X-User-ID", "alice-sub"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    private static final String VALID_BODY = """
            {"type":"RUNNING","duration":30,"caloriesBurned":300}
            """;
}
