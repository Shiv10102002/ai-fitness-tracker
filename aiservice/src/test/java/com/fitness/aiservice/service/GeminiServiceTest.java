package com.fitness.aiservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini is stubbed at the HTTP exchange level; {@code calls} counts attempts. */
class GeminiServiceTest {

    private static final String OVERLOADED = """
            {"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}
            """;

    final AtomicInteger calls = new AtomicInteger();

    @Test
    void returnsRawResponseBody() {
        GeminiService service = service(request -> respond(HttpStatus.OK, "{\"candidates\":[]}"), 2);

        assertThat(service.getRecommendations("prompt")).isEqualTo("{\"candidates\":[]}");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesTransient503_andSucceedsWhenGeminiRecovers() {
        GeminiService service = service(request -> calls.get() == 1
                ? respond(HttpStatus.SERVICE_UNAVAILABLE, OVERLOADED)
                : respond(HttpStatus.OK, "{\"candidates\":[]}"), 2);

        assertThat(service.getRecommendations("prompt")).isEqualTo("{\"candidates\":[]}");
        assertThat(calls).hasValue(2);
    }

    @Test
    void retriesRateLimit429() {
        GeminiService service = service(request -> calls.get() == 1
                ? respond(HttpStatus.TOO_MANY_REQUESTS, "")
                : respond(HttpStatus.OK, "ok"), 2);

        assertThat(service.getRecommendations("prompt")).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void givesUpAfterMaxRetries_andThrowsTheOriginal503() {
        GeminiService service = service(request -> respond(HttpStatus.SERVICE_UNAVAILABLE, OVERLOADED), 2);

        assertThatThrownBy(() -> service.getRecommendations("prompt"))
                .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        assertThat(calls).hasValue(3); // 1 attempt + 2 retries
    }

    @Test
    void doesNotRetryPermanentErrors_likeARetiredModel() {
        GeminiService service = service(request -> respond(HttpStatus.NOT_FOUND,
                "{\"error\":{\"code\":404,\"message\":\"model is no longer available\"}}"), 2);

        assertThatThrownBy(() -> service.getRecommendations("prompt"))
                .isInstanceOf(WebClientResponseException.NotFound.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void timesOutEachAttempt_andFailsWithTimeout() {
        GeminiService service = service(request -> Mono.never(), 1);
        ReflectionTestUtils.setField(service, "timeout", Duration.ofMillis(100));

        assertThatThrownBy(() -> service.getRecommendations("prompt"))
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(calls).hasValue(2); // the timeout is transient, so it was retried once
    }

    private GeminiService service(ExchangeFunction exchange, int maxRetries) {
        ExchangeFunction counting = request -> {
            calls.incrementAndGet();
            return exchange.exchange(request);
        };
        GeminiService service = new GeminiService(WebClient.builder().exchangeFunction(counting));
        ReflectionTestUtils.setField(service, "geminiApiUrl", "http://gemini.test/v1beta/models/m:generateContent");
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "timeout", Duration.ofSeconds(5));
        ReflectionTestUtils.setField(service, "maxRetries", maxRetries);
        ReflectionTestUtils.setField(service, "retryBackoff", Duration.ofMillis(10));
        return service;
    }

    private static Mono<ClientResponse> respond(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
