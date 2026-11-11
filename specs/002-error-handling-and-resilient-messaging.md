# Spec 002 — Consistent error responses and resilient Kafka messaging

| | |
|---|---|
| **Status** | Implemented |
| **Services** | user-service, activity-service, ai-service, gateway |
| **Depends on** | [001](001-identity-from-jwt.md) |

## Problem

- Every failure was a bare `RuntimeException`, so "user not found", "bad input"
  and "database down" all became HTTP 500 with a stack trace in the log.
- `POST /api/activities` accepted any body (negative duration, missing type).
- activity-service treated "user-service is down" the same as "user does not
  exist", so an outage showed up as a misleading validation error.
- Kafka publish failures were swallowed with `e.printStackTrace()`.
- ai-service consumer:
  - a malformed message (poison pill) failed deserialization on every poll,
    blocking the partition forever;
  - a redelivered event created a **duplicate** recommendation;
  - `spring.json.trusted.packages=*` allowed deserializing any class.

## Requirements

### HTTP errors (all MVC services)

1. **E1** — Errors are returned as RFC 9457 `application/problem+json`
   (`ProblemDetail`) through one `@RestControllerAdvice` per service.
2. **E2** — Status mapping:

   | Situation | Status |
   |---|---|
   | Bean-validation failure, malformed JSON, missing `X-User-ID` | 400 (with per-field `errors` map for validation) |
   | Resource not found / not owned by caller | 404 |
   | Downstream service unreachable or 5xx | 503 |
   | Anything else | 500 with a generic message (no internals leaked) |

3. **E3** — `ActivityRequest` is validated: `type` required, `duration` > 0,
   `caloriesBurned` ≥ 0, `startTime` not in the future.
4. **E4** — activity-service distinguishes *user does not exist* (404 from
   user-service → 404) from *user-service unavailable* (→ 503).
5. **E5** — The gateway returns 503 (not 500) when the user-sync call to
   user-service fails.

### Messaging

6. **M1** — activity-service logs publish failures asynchronously with the
   activity id (`KafkaTemplate.send` is async; a `try/catch` around it never saw
   broker errors).
7. **M2** — ai-service wraps the JSON deserializer in `ErrorHandlingDeserializer`.
   Records that cannot be deserialized or processed are retried twice (1 s apart)
   and then published to `activity-events.DLT`, so the partition keeps moving.
8. **M3** — Recommendation processing is idempotent: a unique index on
   `activityId` plus a pre-check means a redelivered event does not create a
   second recommendation.
9. **M4** — `trusted.packages` is restricted to `com.fitness.aiservice.model`.

## Implementation findings

Found by the Testcontainers integration tests, not by the unit tests:

- spring-kafka 4 changed the default dead-letter suffix to `-dlt`. The destination
  `<topic>.DLT` is now set explicitly so it matches this spec and the docs.
- The dead-letter producer must take its bootstrap servers from Boot's
  `KafkaConnectionDetails`, not `KafkaProperties`. Otherwise it targets
  `localhost:9092` while the consumer reads from the real broker, and the consumer
  thread blocks while publishing the dead letter.

## Out of scope / known trade-off

The activity is saved to MongoDB and *then* published to Kafka. If the publish
fails, the activity exists without a recommendation (logged per M1). The proper
fix is a **transactional outbox**. That is noted as future work because it adds
a relay process, which is more than this project needs today.

## Acceptance criteria

- [x] Unknown recommendation returns 404 `problem+json`.
- [x] Invalid activity body returns 400 with field errors and is not persisted.
- [x] user-service 404 → activity-service 404; user-service 5xx / unreachable → 503.
- [x] Gateway user-sync failure → 503.
- [x] A second event for the same `activityId` does not call Gemini again.
- [x] A malformed record goes to `activity-events.DLT` (`RecommendationPipelineIT`).
