package com.fitness.gateway.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;

/** user-service responses are stubbed at the HTTP exchange level (specs/002, E5). */
class UserServiceTest {

    @Test
    void validateUser_returnsUserServiceAnswer() {
        UserService service = serviceRespondingWith(HttpStatus.OK, "true");

        StepVerifier.create(service.validateUser("kc-1")).expectNext(true).verifyComplete();
    }

    @Test
    void validateUser_mapsUserServiceErrorTo503() {
        UserService service = serviceRespondingWith(HttpStatus.INTERNAL_SERVER_ERROR, "");

        StepVerifier.create(service.validateUser("kc-1"))
                .expectErrorMatches(UserServiceTest::isServiceUnavailable)
                .verify();
    }

    @Test
    void registerUser_mapsConnectionFailureTo503() {
        ExchangeFunction refused = request -> Mono.error(new ConnectException("Connection refused"));
        UserService service = new UserService(WebClient.builder().exchangeFunction(refused).build());

        StepVerifier.create(service.registerUser(new RegisterRequest()))
                .expectErrorMatches(UserServiceTest::isServiceUnavailable)
                .verify();
    }

    private static boolean isServiceUnavailable(Throwable e) {
        return e instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
    }

    private static UserService serviceRespondingWith(HttpStatus status, String body) {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return new UserService(WebClient.builder().exchangeFunction(exchange).build());
    }
}
