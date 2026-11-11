package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP client for the Gemini generateContent API.
 *
 * <p>Transient failures (429 rate limit, 5xx such as "model overloaded", timeouts, connection
 * errors) are retried with exponential backoff. Permanent failures (other 4xx, e.g. a retired
 * model or an invalid key) fail immediately. If the call still fails, the caller stores a
 * fallback recommendation.
 *
 * <p>Worst case per activity is (1 + maxRetries) * timeout plus backoff, which must stay below
 * the Kafka consumer's max.poll.interval.ms (5 minutes by default).
 */
@Service
@Slf4j
public class GeminiService {

    private static final int MAX_LOGGED_BODY = 300;

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /** Per attempt. Without a bound, a hanging call blocks the Kafka consumer. */
    @Value("${gemini.api.timeout:60s}")
    private Duration timeout;

    @Value("${gemini.api.max-retries:2}")
    private int maxRetries;

    @Value("${gemini.api.retry-backoff:2s}")
    private Duration retryBackoff;

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getRecommendations(String details) {
        int callNumber = totalCalls.incrementAndGet();
        log.info("Gemini API call #{} initiated | total={} success={} failed={}",
                callNumber, callNumber, successCalls.get(), failedCalls.get());

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", details)
                        })
                }
        );

        try {
            String response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("Content-Type", "application/json")
                    .header("X-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxRetries, retryBackoff)
                            .filter(GeminiService::isTransient)
                            .doBeforeRetry(signal -> log.warn("Gemini API call #{} attempt {} failed [{}], retrying",
                                    callNumber, signal.totalRetries() + 1, describe(signal.failure())))
                            // Surface the real cause (e.g. the 503), not Reactor's RetryExhaustedException.
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

            int success = successCalls.incrementAndGet();
            log.info("Gemini API call #{} succeeded | total={} success={} failed={}",
                    callNumber, totalCalls.get(), success, failedCalls.get());
            return response;

        } catch (RuntimeException e) {
            int failed = failedCalls.incrementAndGet();
            log.error("Gemini API call #{} failed [{}] | total={} success={} failed={}",
                    callNumber, describe(e), totalCalls.get(), successCalls.get(), failed);
            throw e;
        }
    }

    /** 429 and 5xx responses, timeouts and connection failures are worth retrying. */
    static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException e) {
            int status = e.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return t instanceof TimeoutException || t instanceof WebClientRequestException;
    }

    /** Includes Gemini's error body, which says *why* (e.g. "model is overloaded"). */
    private static String describe(Throwable t) {
        Throwable cause = (t.getCause() instanceof TimeoutException) ? t.getCause() : t;
        if (cause instanceof WebClientResponseException e) {
            String body = e.getResponseBodyAsString().replaceAll("\\s+", " ").trim();
            if (body.length() > MAX_LOGGED_BODY) {
                body = body.substring(0, MAX_LOGGED_BODY) + "...";
            }
            return e.getStatusCode() + (body.isEmpty() ? "" : " " + body);
        }
        if (cause instanceof TimeoutException) {
            return "no response within timeout";
        }
        return String.valueOf(cause.getMessage());
    }

    public void logApiCallStats() {
        log.info("Gemini API stats | total={} success={} failed={}",
                totalCalls.get(), successCalls.get(), failedCalls.get());
    }
}
