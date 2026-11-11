package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.ResourceNotFoundException;
import com.fitness.userservice.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository repository;

    /**
     * Idempotent registration keyed on the Keycloak subject id.
     * <ul>
     *   <li>Known keyCloakId: returns the existing user.</li>
     *   <li>Known email without a keyCloakId (legacy row): links it to the keyCloakId.</li>
     *   <li>Otherwise: creates a new user.</li>
     * </ul>
     */
    @Transactional
    public UserResponse register(RegisterRequest req) {
        return repository.findByKeyCloakId(req.getKeyCloakId())
                .or(() -> repository.findByEmail(req.getEmail())
                        .filter(existing -> existing.getKeyCloakId() == null)
                        .map(existing -> {
                            log.info("Linking existing user {} to keyCloakId {}", existing.getId(), req.getKeyCloakId());
                            existing.setKeyCloakId(req.getKeyCloakId());
                            return repository.save(existing);
                        }))
                .map(UserResponse::from)
                .orElseGet(() -> UserResponse.from(repository.save(newUser(req))));
    }

    public UserResponse getByKeyCloakId(String keyCloakId) {
        return repository.findByKeyCloakId(keyCloakId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public boolean existsByKeyCloakId(String keyCloakId) {
        log.debug("Checking whether user with keyCloakId {} exists", keyCloakId);
        return repository.existsByKeyCloakId(keyCloakId);
    }

    private static User newUser(RegisterRequest req) {
        User user = new User();
        user.setKeyCloakId(req.getKeyCloakId());
        user.setEmail(req.getEmail());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        return user;
    }
}
