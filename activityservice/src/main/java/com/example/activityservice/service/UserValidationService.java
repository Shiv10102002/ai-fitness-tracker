package com.example.activityservice.service;

import com.example.activityservice.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Asks user-service whether a user exists. "Does not exist" and "cannot tell because
 * user-service is down" are deliberately different outcomes (specs/002, E4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {
    private final WebClient userServiceWebClient;

    /**
     * @return whether the user exists
     * @throws ServiceUnavailableException if user-service is unreachable or returns 5xx
     */
    public boolean validateUser(String userId) {
        log.debug("Validating user {} against user-service", userId);
        try {
            return Boolean.TRUE.equals(userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new ServiceUnavailableException("user-service returned " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            // Connection refused, timeout, no instance in Eureka, ...
            throw new ServiceUnavailableException("user-service is unavailable", e);
        }
    }
}
