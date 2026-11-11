package com.example.activityservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Load-balanced (Eureka) builder. The observation registry makes every call a child span
     * and propagates the W3C {@code traceparent} header downstream (specs/003, T2).
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder(ObjectProvider<ObservationRegistry> observationRegistry) {
        return WebClient.builder()
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.baseUrl("http://USER-SERVICE").build();
    }
}
