package com.example.activityservice.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * specs/003, T2: service-to-service calls must be observed, which is what makes the tracer
 * create a client span and inject the {@code traceparent} header.
 */
class WebClientConfigTest {

    @Test
    void userServiceCalls_areObservedAsHttpClientRequests() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("observationRegistry", registry);

        WebClient client = new WebClientConfig()
                .webClientBuilder(beans.getBeanProvider(ObservationRegistry.class))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("true")
                        .build()))
                .build();

        client.get().uri("http://user-service/api/users/kc-1/validate").retrieve().bodyToMono(Boolean.class).block();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("http.client.requests")
                .that()
                .hasBeenStarted()
                .hasBeenStopped();
    }
}
