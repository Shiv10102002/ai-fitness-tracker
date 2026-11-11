package com.fitness.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/** Gateway access rules: internal user-service endpoints are never reachable by clients. */
@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void unauthenticatedRequest_isRejectedWith401() {
        client.get().uri("/api/activities").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void registerEndpoint_isForbiddenEvenForAuthenticatedClients() {
        client.mutateWith(mockJwt().jwt(j -> j.subject("alice-sub")))
                .post().uri("/api/users/register")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void validateEndpoint_isForbiddenEvenForAuthenticatedClients() {
        client.mutateWith(mockJwt().jwt(j -> j.subject("alice-sub")))
                .get().uri("/api/users/victim-sub/validate")
                .exchange()
                .expectStatus().isForbidden();
    }
}
