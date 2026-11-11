package com.example.activityservice.controller;

import com.example.activityservice.dto.ActivityRequest;
import com.example.activityservice.dto.ActivityResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
