package com.fitness.userservice;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserRepository;
import com.fitness.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end registration against a real PostgreSQL (Testcontainers). Requires Docker. */
@SpringBootTest
@Testcontainers
class UserRegistrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserService userService;

    @Autowired
    UserRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void registeringSameKeyCloakIdTwice_createsExactlyOneUser() {
        UserResponse first = userService.register(request("kc-1", "alice@example.com"));
        UserResponse second = userService.register(request("kc-1", "alice@example.com"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(userService.existsByKeyCloakId("kc-1")).isTrue();
    }

    @Test
    void persistedUser_isReadableByKeyCloakId() {
        userService.register(request("kc-2", "bob@example.com"));

        UserResponse me = userService.getByKeyCloakId("kc-2");

        assertThat(me.getEmail()).isEqualTo("bob@example.com");
        assertThat(me.getCreatedAt()).isNotNull();
    }

    private static RegisterRequest request(String keyCloakId, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setKeyCloakId(keyCloakId);
        req.setEmail(email);
        return req;
    }
}
