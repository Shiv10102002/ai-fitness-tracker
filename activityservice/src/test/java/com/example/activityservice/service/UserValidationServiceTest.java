package com.example.activityservice.service;

import com.example.activityservice.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** user-service responses are stubbed at the HTTP exchange level, so no server is needed. */
class UserValidationServiceTest {

    @Test
    void returnsTrue_whenUserServiceConfirmsUser() {
        assertThat(serviceRespondingWith(HttpStatus.OK, "true").validateUser("kc-1")).isTrue();
    }

    @Test
    void returnsFalse_whenUserServiceSaysUserDoesNotExist() {
        assertThat(serviceRespondingWith(HttpStatus.OK, "false").validateUser("ghost")).isFalse();
    }

    @Test
    void returnsFalse_whenUserServiceReturns404() {
        assertThat(serviceRespondingWith(HttpStatus.NOT_FOUND, "").validateUser("ghost")).isFalse();
    }

    @Test
    void throwsServiceUnavailable_whenUserServiceReturns5xx() {
        UserValidationService service = serviceRespondingWith(HttpStatus.INTERNAL_SERVER_ERROR, "");

        assertThatThrownBy(() -> service.validateUser("kc-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    void throwsServiceUnavailable_whenUserServiceIsUnreachable() {
        ExchangeFunction refused = request -> Mono.error(new ConnectException("Connection refused"));
        UserValidationService service = new UserValidationService(WebClient.builder().exchangeFunction(refused).build());

        assertThatThrownBy(() -> service.validateUser("kc-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("user-service is unavailable");
    }

    private static UserValidationService serviceRespondingWith(HttpStatus status, String body) {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return new UserValidationService(WebClient.builder().exchangeFunction(exchange).build());
    }
}
