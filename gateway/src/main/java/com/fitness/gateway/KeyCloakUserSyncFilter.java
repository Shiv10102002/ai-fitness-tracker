package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Propagates the caller's identity to downstream services and lazily registers
 * Keycloak users in user-service.
 *
 * <p>Identity is taken <b>only</b> from the JWT that Spring Security has already
 * validated (signature, issuer, expiry). Any {@code X-User-ID} header sent by the
 * client is discarded, so a caller cannot impersonate another user. See
 * {@code specs/001-identity-from-jwt.md}.
 *
 * <p>Runs after Spring Security's {@code WebFilterChainProxy} (order -100), so the
 * authenticated principal is available on the exchange.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class KeyCloakUserSyncFilter implements WebFilter {

    public static final String USER_ID_HEADER = "X-User-ID";

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = withUserIdHeader(exchange, null);

        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .flatMap(jwt -> syncUser(jwt).thenReturn(withUserIdHeader(exchange, jwt.getSubject())))
                .defaultIfEmpty(sanitized)
                .flatMap(chain::filter);
    }

    private Mono<Void> syncUser(Jwt jwt) {
        String userId = jwt.getSubject();
        return userService.validateUser(userId)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<Void>empty();
                    }
                    log.info("First request from user {}, registering in user-service", userId);
                    return userService.registerUser(toRegisterRequest(jwt)).then();
                });
    }

    static RegisterRequest toRegisterRequest(Jwt jwt) {
        RegisterRequest request = new RegisterRequest();
        request.setKeyCloakId(jwt.getSubject());
        request.setEmail(jwt.getClaimAsString("email"));
        request.setFirstName(jwt.getClaimAsString("given_name"));
        request.setLastName(jwt.getClaimAsString("family_name"));
        return request;
    }

    /** Removes any client-supplied user id header and, if {@code userId} is non-null, sets the trusted one. */
    private static ServerWebExchange withUserIdHeader(ServerWebExchange exchange, String userId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }
}
