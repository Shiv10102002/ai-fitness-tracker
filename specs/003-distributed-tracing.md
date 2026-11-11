# Spec 003 — Distributed tracing across HTTP and Kafka

| | |
|---|---|
| **Status** | Implemented (manual end-to-end check pending) |
| **Services** | gateway, user-service, activity-service, ai-service |
| **Depends on** | [002](002-error-handling-and-resilient-messaging.md) |

## Problem

One user action crosses up to five processes:

```
browser → gateway → user-service (sync) → activity-service → user-service (validate)
                                              └─ Kafka ─→ ai-service → Gemini → MongoDB
```

When something fails (e.g. "my activity never got a recommendation"), each service
logs on its own and nothing ties the logs together. Debugging means guessing
timestamps across six terminals.

## Requirements

1. **T1** — Every request entering the gateway starts (or continues) a trace using
   W3C Trace Context (`traceparent` header).
2. **T2** — The trace is propagated on every service-to-service HTTP call
   (gateway → user-service, activity-service → user-service, ai-service → Gemini).
   *Finding:* each service built its own `WebClient.Builder` with a bare
   `WebClient.builder()` that had no `ObservationRegistry`, so no client spans were
   created and no context was propagated. (In Boot 4, `spring-boot-starter-webflux`
   does not auto-configure a `WebClient.Builder`.) Builders MUST set the
   application's `ObservationRegistry`.
3. **T3** — The trace is propagated **through Kafka**: the producer adds `traceparent`
   to record headers and the `@KafkaListener` continues the same trace, so the AI
   analysis appears as a child of the original `POST /api/activities`.
4. **T4** — Every log line includes `traceId` and `spanId`, so a single
   `grep <traceId>` across all services returns the full story.
5. **T5** — Error responses (`ProblemDetail`) include `traceId`, so a user-reported
   error can be found directly in the trace UI.
6. **T6** — Spans are exported over OTLP/HTTP to Jaeger (`docker compose` service,
   UI on http://localhost:16686). Local sampling is 100%. The endpoint and
   probability are configurable through env vars.

## Design notes

- Micrometer Observation + Micrometer Tracing with the OpenTelemetry bridge
  (`spring-boot-starter-opentelemetry`), the Spring Boot 4 default.
- Kafka observation is opt-in: `spring.kafka.template.observation-enabled` and
  `spring.kafka.listener.observation-enabled`.
- Jaeger was chosen over Zipkin because it accepts OTLP natively, so services do not
  depend on a vendor-specific exporter.
- OTLP **metrics** export is disabled (Jaeger only stores traces).
- **Finding:** in Boot 4, `management.tracing.export.enabled=false` also leaves
  OpenTelemetry without a working tracer, so **no context is propagated** and services
  silently lose correlation. To stop sending spans (tests, or no collector), set
  `management.tracing.export.otlp.enabled=false` instead. `ActivityTrackingIT` caught this.

## Out of scope

Centralised log aggregation (Loki/ELK) and metrics dashboards (Prometheus/Grafana).

## Acceptance criteria

- [x] `WebClient` builders apply the `ObservationRegistry`, so calls are observed as
      `http.client.requests` (`WebClientConfigTest`).
- [x] `ProblemDetail` for a 500 contains `traceId` when a span is active.
- [x] Kafka records published by activity-service carry a `traceparent` header (`ActivityTrackingIT`).
- [ ] Manual: one `POST /api/activities` produces a single trace in Jaeger spanning
      gateway, user-service, activity-service, Kafka and ai-service.
