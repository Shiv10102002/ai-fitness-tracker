package com.fitness.userservice.controller;

import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.ResourceNotFoundException;
import com.fitness.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @Test
    void me_returnsCallersProfile_withoutPassword() throws Exception {
        UserResponse alice = new UserResponse();
        alice.setId("uuid-1");
        alice.setKeyCloakId("kc-1");
        alice.setEmail("alice@example.com");
        when(userService.getByKeyCloakId("kc-1")).thenReturn(alice);

        mockMvc.perform(get("/api/users/me").header("X-User-ID", "kc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_isRejected_whenGatewayIdentityHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_rejectsRequestWithoutKeyCloakId() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).register(any());
    }

    @Test
    void register_rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyCloakId\":\"kc-1\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_returnsWhetherUserExists() throws Exception {
        when(userService.existsByKeyCloakId("kc-1")).thenReturn(true);

        mockMvc.perform(get("/api/users/kc-1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void me_returns404ProblemDetail_whenUserIsNotSyncedYet() throws Exception {
        when(userService.getByKeyCloakId("kc-unknown")).thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/users/me").header("X-User-ID", "kc-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void register_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keyCloakId").value("keyCloakId is required"))
                .andExpect(jsonPath("$.errors.email").value("Invalid email format"));
    }
}
