package com.example.activityservice.exception;

import com.example.activityservice.controller.ActivityController;
import com.example.activityservice.service.ActivityService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** specs/003, T5: error responses carry the trace id so they can be found in Jaeger. */
@WebMvcTest(ActivityController.class)
class ProblemDetailTraceIdTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ActivityService activityService;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    Tracer tracer;

    @Test
    void unexpectedError_includesTraceId() throws Exception {
        when(tracer.currentSpan().context().traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(activityService.getUserActivities("alice-sub")).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/activities").header("X-User-ID", "alice-sub"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void springMvcErrors_alsoIncludeTraceId() throws Exception {
        when(tracer.currentSpan().context().traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");

        mockMvc.perform(get("/api/activities")) // missing X-User-ID -> handled by Spring MVC base handler
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }
}
