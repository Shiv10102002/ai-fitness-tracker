package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserResponse;
import com.fitness.gateway.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Acceptance criteria of specs/001-identity-from-jwt.md at the gateway. */
@ExtendWith(MockitoExtension.class)
class KeyCloakUserSyncFilterTest {

    @Mock
    UserService userService;

    KeyCloakUserSyncFilter filter;

    /** Captures the exchange that the filter passes on to the rest of the chain. */
    final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    final WebFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        filter = new KeyCloakUserSyncFilter(userService);
    }

    @Test
    void spoofedUserIdHeader_isReplacedWithJwtSubject() {
        when(userService.validateUser("alice-sub")).thenReturn(Mono.just(true));
        ServerWebExchange exchange = authenticated(
                MockServerHttpRequest.get("/api/activities").header("X-User-ID", "victim-sub"),
                jwt("alice-sub"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().get("X-User-ID")).containsExactly("alice-sub");
    }

    @Test
    void unauthenticatedRequest_hasClientUserIdHeaderStripped() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").header("X-User-ID", "victim-sub"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().containsHeader("X-User-ID")).isFalse();
        verifyNoInteractions(userService);
    }

    @Test
    void firstRequestOfNewUser_registersUserFromJwtClaims() {
        when(userService.validateUser("alice-sub")).thenReturn(Mono.just(false));
        when(userService.registerUser(any())).thenReturn(Mono.just(new UserResponse()));
        ServerWebExchange exchange = authenticated(MockServerHttpRequest.get("/api/activities"), jwt("alice-sub"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).registerUser(captor.capture());
        assertThat(captor.getValue().getKeyCloakId()).isEqualTo("alice-sub");
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-ID")).isEqualTo("alice-sub");
    }

    @Test
    void existingUser_isNotRegisteredAgain() {
        when(userService.validateUser("alice-sub")).thenReturn(Mono.just(true));
        ServerWebExchange exchange = authenticated(MockServerHttpRequest.get("/api/activities"), jwt("alice-sub"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(userService, never()).registerUser(any());
    }

    private static ServerWebExchange authenticated(MockServerHttpRequest.BaseBuilder<?> request, Jwt jwt) {
        return MockServerWebExchange.from(request).mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt)))
                .build();
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("email", "alice@example.com")
                .claim("given_name", "Alice")
                .claim("family_name", "Smith")
                .build();
    }
}
