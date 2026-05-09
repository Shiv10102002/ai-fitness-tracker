package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeyCloakUserSyncFilter implements WebFilter {
    private final UserService userService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token == null) {
            return chain.filter(exchange);
        }

        RegisterRequest registerRequest = getUserDetails(token);
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");

        if (userId == null) {
            userId = registerRequest.getKeyCloakId();
        }

        if (userId != null) {
            String finalUserId = userId;
            return userService.validateUser(userId).flatMap(exist -> {
                if (!exist) {
                    return userService.registerUser(registerRequest)
                            .then(Mono.empty());
                } else {
                    log.info("user already exist ,Skipping sync");
                    return Mono.empty();
                }
            }).then(Mono.defer(() -> {
                ServerHttpRequest mutatedReq = exchange.getRequest().mutate()
                        .header("X-User-ID", finalUserId)
                        .build();
                return chain.filter(exchange.mutate().request(mutatedReq).build());
            }));
        }

        return chain.filter(exchange);
    }

    private RegisterRequest getUserDetails(String token){

        try {
            String tokenWithoutBearer = token.replace("Bearer","").trim();
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            RegisterRequest request = new RegisterRequest();
            request.setEmail(claimsSet.getStringClaim("email"));
            request.setKeyCloakId(claimsSet.getStringClaim("sub"));
            request.setPassword("dummy@123");
            request.setFirstName(claimsSet.getStringClaim("given_name"));
            request.setLastName(claimsSet.getStringClaim("family_name"));

            return request;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }


}
