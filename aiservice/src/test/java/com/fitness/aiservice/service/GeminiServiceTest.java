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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiServiceTest {

    @Test
    void returnsRawResponseBody() {
        GeminiService service = service(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"candidates\":[]}")
                .build()), Duration.ofSeconds(5));

        assertThat(service.getRecommendations("prompt")).isEqualTo("{\"candidates\":[]}");
    }

    @Test
    void failsFast_whenGeminiDoesNotAnswerWithinTimeout() {
        GeminiService service = service(request -> Mono.never(), Duration.ofMillis(100));

        assertThatThrownBy(() -> service.getRecommendations("prompt"))
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void propagatesHttpErrors_soTheListenerCanStoreAFallback() {
        GeminiService service = service(request -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()),
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.getRecommendations("prompt"))
                .isInstanceOf(WebClientResponseException.NotFound.class);
    }

    private static GeminiService service(ExchangeFunction exchange, Duration timeout) {
        GeminiService service = new GeminiService(WebClient.builder().exchangeFunction(exchange));
        ReflectionTestUtils.setField(service, "geminiApiUrl", "http://gemini.test/v1beta/models/m:generateContent");
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "timeout", timeout);
        return service;
    }
}
