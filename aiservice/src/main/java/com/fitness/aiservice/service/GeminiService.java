package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /** Without a bound, a hanging call blocks the Kafka consumer past max.poll.interval.ms. */
    @Value("${gemini.api.timeout:60s}")
    private Duration timeout;

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);

    public GeminiService(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder.build();
    }

    public String getRecommendations(String details){
        int callNumber = totalCalls.incrementAndGet();
        log.info("Gemini API call #{} initiated | total={} success={} failed={}",
                callNumber, callNumber, successCalls.get(), failedCalls.get());

        Map<String,Object> requestBody = Map.of(
                "contents",new Object[]{
                        Map.of("parts",new Object[]{
                                Map.of("text",details)
                        })
                }
        );

        try {
            String response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("Content-Type","application/json")
                    .header("X-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            int success = successCalls.incrementAndGet();
            log.info("Gemini API call #{} succeeded | total={} success={} failed={}",
                    callNumber, totalCalls.get(), success, failedCalls.get());
            return response;

        } catch (Exception e) {
            int failed = failedCalls.incrementAndGet();
            log.error("Gemini API call #{} failed [{}] | total={} success={} failed={}",
                    callNumber, e.getMessage(), totalCalls.get(), successCalls.get(), failed);
            throw e;
        }
    }

    public void logApiCallStats() {
        log.info("Gemini API stats | total={} success={} failed={}",
                totalCalls.get(), successCalls.get(), failedCalls.get());
    }
}
