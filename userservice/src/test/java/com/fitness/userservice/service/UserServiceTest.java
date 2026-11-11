package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository repository;

    @InjectMocks
    UserService userService;

    @Test
    void register_createsNewUser_whenKeyCloakIdAndEmailAreUnknown() {
        RegisterRequest req = request("kc-1", "alice@example.com");
        when(repository.findByKeyCloakId("kc-1")).thenReturn(Optional.empty());
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("uuid-1");
            return u;
        });

        UserResponse response = userService.register(req);

        assertThat(response.getId()).isEqualTo("uuid-1");
        assertThat(response.getKeyCloakId()).isEqualTo("kc-1");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");
    }

    @Test
    void register_isIdempotent_whenKeyCloakIdAlreadyExists() {
        User existing = user("uuid-1", "kc-1", "alice@example.com");
        when(repository.findByKeyCloakId("kc-1")).thenReturn(Optional.of(existing));

        UserResponse response = userService.register(request("kc-1", "alice@example.com"));

        assertThat(response.getId()).isEqualTo("uuid-1");
        verify(repository, never()).save(any());
    }

    @Test
    void register_linksLegacyUser_whenEmailExistsWithoutKeyCloakId() {
        User legacy = user("uuid-legacy", null, "alice@example.com");
        when(repository.findByKeyCloakId("kc-1")).thenReturn(Optional.empty());
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(legacy));
        when(repository.save(legacy)).thenReturn(legacy);

        UserResponse response = userService.register(request("kc-1", "alice@example.com"));

        assertThat(response.getId()).isEqualTo("uuid-legacy");
        assertThat(response.getKeyCloakId()).isEqualTo("kc-1");
        verify(repository).save(legacy);
    }

    @Test
    void getByKeyCloakId_throws_whenUserDoesNotExist() {
        when(repository.findByKeyCloakId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByKeyCloakId("missing"))
                .hasMessageContaining("User not found");
    }

    @Test
    void existsByKeyCloakId_delegatesToRepository() {
        when(repository.existsByKeyCloakId("kc-1")).thenReturn(true);

        assertThat(userService.existsByKeyCloakId("kc-1")).isTrue();
    }

    private static RegisterRequest request(String keyCloakId, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setKeyCloakId(keyCloakId);
        req.setEmail(email);
        req.setFirstName("Alice");
        req.setLastName("Smith");
        return req;
    }

    private static User user(String id, String keyCloakId, String email) {
        User user = new User();
        user.setId(id);
        user.setKeyCloakId(keyCloakId);
        user.setEmail(email);
        return user;
    }
}
