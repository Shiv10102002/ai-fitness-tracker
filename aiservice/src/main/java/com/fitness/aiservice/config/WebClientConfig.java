package com.fitness.aiservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** Gemini calls appear as spans in the activity's trace (specs/003, T2). */
    @Bean
    public WebClient.Builder webClientBuilder(ObjectProvider<ObservationRegistry> observationRegistry) {
        return WebClient.builder()
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
    }
}
