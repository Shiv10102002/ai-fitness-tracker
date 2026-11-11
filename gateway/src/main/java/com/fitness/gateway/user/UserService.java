package com.fitness.gateway.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service-to-service client for user-service (resolved through Eureka).
 * Any failure is surfaced to the caller as 503: the request cannot proceed without a
 * synced user, and it is not the client's fault (specs/002, E5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        log.debug("Validating user {} against user-service", userId);
        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorMap(e -> unavailable("validate user " + userId, e));
    }

    public Mono<UserResponse> registerUser(RegisterRequest request) {
        log.info("Registering user {} in user-service", request.getKeyCloakId());
        return userServiceWebClient.post()
                .uri("/api/users/register")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .onErrorMap(e -> unavailable("register user " + request.getKeyCloakId(), e));
    }

    private static ResponseStatusException unavailable(String action, Throwable cause) {
        log.error("user-service call failed: {}", action, cause);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable", cause);
    }
}
